package com.huah.ai.platform.auth.controller;

import com.huah.ai.platform.auth.mapper.AiUserMapper;
import com.huah.ai.platform.auth.mapper.BotPermissionMapper;
import com.huah.ai.platform.auth.model.AiUser;
import com.huah.ai.platform.auth.model.BotPermission;
import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 认证接口 -- JWT 登录 / 登出 / 刷新 / 验证
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final AiUserMapper userMapper;
    private final BotPermissionMapper botPermissionMapper;

    private static final String TOKEN_BLACKLIST_PREFIX = "ai:token:blacklist:";

    /**
     * 登录 -- 支持数据库用户 + 演示 fallback（admin/admin123）
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            return Result.fail(400, "用户名和密码不能为空");
        }

        // 1. 尝试从数据库查询用户
        AiUser user = userMapper.selectByUsernameAndEnabled(username);

        String roles;
        String department;
        String userId;

        if (user != null) {
            // 数据库用户 - 验证密码
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                log.warn("登录失败 - 密码错误: username={}", username);
                return Result.fail(401, "用户名或密码错误");
            }
            roles = user.getRoles() != null ? user.getRoles() : "ROLE_USER";
            department = user.getDepartment() != null ? user.getDepartment() : "未知部门";
            userId = user.getId();

            // 更新最后登录时间
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.updateById(user);
        } else {
            // 演示 fallback -- admin/admin123 直接通过
            if ("admin".equals(username) && "admin123".equals(password)) {
                roles = "ROLE_ADMIN,ROLE_RD,ROLE_SALES,ROLE_HR,ROLE_USER";
                department = "系统管理";
                userId = "SYS-ADMIN-001";
                log.info("演示用户登录: {}", username);
            } else {
                log.warn("登录失败 - 用户不存在: username={}", username);
                return Result.fail(401, "用户名或密码错误");
            }
        }

        // 2. 生成 JWT
        Map<String, Object> claims = Map.of(
                "username",   username,
                "department", department,
                "roles",      roles,
                "userId",     userId
        );
        String token = jwtUtil.generateToken(username, claims);
        log.info("用户登录成功: username={}, roles={}", username, roles);

        return Result.ok(Map.of(
                "token",     token,
                "tokenType", "Bearer",
                "expiresIn", 86400,
                "userId",    userId,
                "username",  username,
                "roles",     roles,
                "department",department
        ));
    }

    /**
     * 登出 -- Token 加入 Redis 黑名单
     */
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = "Authorization", defaultValue = "") String authorization) {
        if (authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            redisTemplate.opsForValue().set(
                    TOKEN_BLACKLIST_PREFIX + token, "1", 24, TimeUnit.HOURS);
            log.info("Token 已加入黑名单");
        }
        return Result.ok();
    }

    /**
     * 刷新 Token
     */
    @PostMapping("/refresh")
    public Result<Map<String, String>> refresh(@RequestBody Map<String, String> body) {
        String oldToken = body.get("token");
        if (!jwtUtil.validateToken(oldToken)) {
            return Result.fail(401, "Token 无效或已过期");
        }
        // 检查黑名单
        if (Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + oldToken))) {
            return Result.fail(401, "Token 已失效");
        }
        String subject = jwtUtil.getSubject(oldToken);
        String newToken = jwtUtil.generateToken(subject, Map.of());
        return Result.ok(Map.of("token", newToken));
    }

    /**
     * 验证 Token（供其他微服务内部调用）
     */
    @GetMapping("/validate")
    public Result<Map<String, Object>> validate(
            @RequestHeader(value = "Authorization", defaultValue = "") String authorization) {
        if (!authorization.startsWith("Bearer ")) {
            return Result.fail(401, "Authorization header 缺失");
        }
        String token = authorization.substring(7);

        if (Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + token))) {
            return Result.fail(401, "Token 已失效");
        }
        if (!jwtUtil.validateToken(token)) {
            return Result.fail(401, "Token 无效");
        }

        String userId = jwtUtil.getSubject(token);
        Object roles = jwtUtil.getClaim(token, "roles");
        Object dept  = jwtUtil.getClaim(token, "department");

        return Result.ok(Map.of(
                "userId",     userId != null ? userId : "",
                "roles",      roles != null ? roles : "",
                "department", dept  != null ? dept  : ""
        ));
    }

    /**
     * 获取当前用户可用的 Bot 列表（任何已认证用户均可调用）
     * ADMIN 可见所有已启用 Bot，其他用户按角色/部门过滤
     */
    @GetMapping("/my-bots")
    public Result<List<BotPermission>> myBots(HttpServletRequest request) {
        String roles = (String) request.getAttribute("X-Roles");
        String department = (String) request.getAttribute("X-Department");

        List<BotPermission> all = botPermissionMapper.selectList(null);
        List<BotPermission> available = all.stream()
                .filter(BotPermission::isEnabled)
                .filter(p -> {
                    // ADMIN 可见全部
                    if (roles != null && roles.contains("ROLE_ADMIN")) return true;
                    // 角色匹配
                    if (p.getAllowedRoles() != null && !p.getAllowedRoles().isBlank() && roles != null) {
                        for (String r : roles.split(",")) {
                            if (p.getAllowedRoles().contains(r.trim())) return true;
                        }
                    }
                    // 部门匹配
                    if (p.getAllowedDepartments() != null && !p.getAllowedDepartments().isBlank() && department != null) {
                        if (p.getAllowedDepartments().contains(department.trim())) return true;
                    }
                    return false;
                })
                .toList();

        return Result.ok(available);
    }

    /**
     * 初始化演示用户（首次部署调用一次）
     */
    @PostMapping("/init-demo-users")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<String> initDemoUsers() {
        String[] users = {"admin", "rd_user", "sales_user", "hr_user", "finance_user"};
        String[] roles = {
            "ROLE_ADMIN,ROLE_RD,ROLE_SALES,ROLE_HR,ROLE_USER",
            "ROLE_RD,ROLE_USER",
            "ROLE_SALES,ROLE_USER",
            "ROLE_HR,ROLE_USER",
            "ROLE_FINANCE,ROLE_USER"
        };
        String[] depts = {"系统管理", "研发中心", "销售部", "人力资源部", "财务部"};

        for (int i = 0; i < users.length; i++) {
            if (userMapper.selectByUsername(users[i]) == null) {
                userMapper.insert(AiUser.builder()
                        .id(UUID.randomUUID().toString())
                        .username(users[i])
                        .passwordHash(passwordEncoder.encode("admin123"))
                        .roles(roles[i])
                        .department(depts[i])
                        .employeeId("EMP00" + (i + 1))
                        .build());
            }
        }
        return Result.ok("演示用户初始化完成");
    }

    // ===== User Management (ADMIN only) =====

    /**
     * 获取所有用户列表
     */
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<List<AiUser>> listUsers() {
        List<AiUser> users = userMapper.selectList(null);
        users.forEach(u -> u.setPasswordHash(null));
        return Result.ok(users);
    }

    /**
     * 获取单个用户
     */
    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<AiUser> getUser(@PathVariable(name = "id") String id) {
        AiUser user = userMapper.selectById(id);
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }
        user.setPasswordHash(null);
        return Result.ok(user);
    }

    /**
     * 创建用户
     */
    @PostMapping("/users")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<AiUser> createUser(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Result.fail(400, "用户名和密码不能为空");
        }
        if (userMapper.selectByUsername(username) != null) {
            return Result.fail(400, "用户名已存在");
        }
        AiUser user = AiUser.builder()
                .id(UUID.randomUUID().toString())
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .department(body.getOrDefault("department", ""))
                .employeeId(body.getOrDefault("employeeId", ""))
                .roles(body.getOrDefault("roles", "ROLE_USER"))
                .build();
        userMapper.insert(user);
        user.setPasswordHash(null);
        log.info("创建用户: username={}", username);
        return Result.ok(user);
    }

    /**
     * 更新用户
     */
    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<AiUser> updateUser(@PathVariable(name = "id") String id, @RequestBody Map<String, String> body) {
        AiUser user = userMapper.selectById(id);
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }
        if (body.containsKey("department")) user.setDepartment(body.get("department"));
        if (body.containsKey("employeeId")) user.setEmployeeId(body.get("employeeId"));
        if (body.containsKey("roles")) user.setRoles(body.get("roles"));
        if (body.containsKey("enabled")) user.setEnabled(Boolean.parseBoolean(body.get("enabled")));
        String password = body.get("password");
        if (password != null && !password.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }
        userMapper.updateById(user);
        user.setPasswordHash(null);
        log.info("更新用户: id={}, username={}", id, user.getUsername());
        return Result.ok(user);
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Void> deleteUser(@PathVariable(name = "id") String id) {
        AiUser user = userMapper.selectById(id);
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }
        userMapper.deleteById(id);
        log.info("删除用户: id={}, username={}", id, user.getUsername());
        return Result.ok();
    }

    // ===== Bot Permission Management (ADMIN only) =====

    /**
     * 获取所有 Bot 权限配置
     */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<List<BotPermission>> listPermissions() {
        return Result.ok(botPermissionMapper.selectList(null));
    }

    /**
     * 获取单个 Bot 权限配置
     */
    @GetMapping("/permissions/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<BotPermission> getPermission(@PathVariable(name = "id") String id) {
        BotPermission perm = botPermissionMapper.selectById(id);
        if (perm == null) {
            return Result.fail(404, "权限配置不存在");
        }
        return Result.ok(perm);
    }

    /**
     * 新增 Bot 权限配置
     */
    @PostMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<BotPermission> createPermission(@RequestBody Map<String, Object> body) {
        String botType = (String) body.get("botType");
        if (botType == null || botType.isBlank()) {
            return Result.fail(400, "botType 不能为空");
        }
        // 检查 botType 唯一性
        BotPermission existing = botPermissionMapper.selectByBotTypeAndEnabled(botType);
        if (existing != null) {
            return Result.fail(400, "Bot 类型 '" + botType + "' 的权限配置已存在");
        }

        BotPermission perm = BotPermission.builder()
                .botType(botType)
                .allowedRoles(body.containsKey("allowedRoles") ? (String) body.get("allowedRoles") : "ROLE_ADMIN")
                .allowedDepartments(body.containsKey("allowedDepartments") ? (String) body.get("allowedDepartments") : null)
                .dataScope(body.containsKey("dataScope") ? (String) body.get("dataScope") : "DEPARTMENT")
                .allowedOperations(body.containsKey("allowedOperations") ? (String) body.get("allowedOperations") : "READ,WRITE")
                .dailyTokenLimit(body.containsKey("dailyTokenLimit") ? ((Number) body.get("dailyTokenLimit")).intValue() : 100000)
                .enabled(body.containsKey("enabled") ? (Boolean) body.get("enabled") : true)
                .build();

        botPermissionMapper.insert(perm);
        log.info("新增 Bot 权限: botType={}, allowedRoles={}", botType, perm.getAllowedRoles());
        return Result.ok(perm);
    }

    /**
     * 更新 Bot 权限配置
     */
    @PutMapping("/permissions/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<BotPermission> updatePermission(@PathVariable(name = "id") String id, @RequestBody Map<String, Object> body) {
        BotPermission perm = botPermissionMapper.selectById(id);
        if (perm == null) {
            return Result.fail(404, "权限配置不存在");
        }
        if (body.containsKey("allowedRoles")) perm.setAllowedRoles((String) body.get("allowedRoles"));
        if (body.containsKey("allowedDepartments")) perm.setAllowedDepartments((String) body.get("allowedDepartments"));
        if (body.containsKey("dataScope")) perm.setDataScope((String) body.get("dataScope"));
        if (body.containsKey("allowedOperations")) perm.setAllowedOperations((String) body.get("allowedOperations"));
        if (body.containsKey("dailyTokenLimit")) perm.setDailyTokenLimit(((Number) body.get("dailyTokenLimit")).intValue());
        if (body.containsKey("enabled")) perm.setEnabled((Boolean) body.get("enabled"));
        botPermissionMapper.updateById(perm);
        log.info("更新 Bot 权限: id={}, botType={}", id, perm.getBotType());
        return Result.ok(perm);
    }

    /**
     * 删除 Bot 权限配置
     */
    @DeleteMapping("/permissions/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Void> deletePermission(@PathVariable(name = "id") String id) {
        BotPermission perm = botPermissionMapper.selectById(id);
        if (perm == null) {
            return Result.fail(404, "权限配置不存在");
        }
        botPermissionMapper.deleteById(id);
        log.info("删除 Bot 权限: id={}, botType={}", id, perm.getBotType());
        return Result.ok();
    }
}
