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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String TOKEN_BLACKLIST_PREFIX = "ai:token:blacklist:";
    private static final String TOKEN_JTI_BLACKLIST_PREFIX = "ai:token:blacklist:jti:";
    private static final String MSG_USERNAME_PASSWORD_REQUIRED = "用户名和密码不能为空";
    private static final String MSG_INVALID_CREDENTIALS = "用户名或密码错误";
    private static final String MSG_TOKEN_INVALID_OR_EXPIRED = "Token 无效或已过期";
    private static final String MSG_TOKEN_REVOKED = "Token 已失效";
    private static final String MSG_AUTH_HEADER_MISSING = "Authorization header 缺失";
    private static final String MSG_TOKEN_INVALID = "Token 无效";
    private static final String MSG_USER_NOT_FOUND = "用户不存在";
    private static final String MSG_USERNAME_EXISTS = "用户名已存在";
    private static final String MSG_PERMISSION_NOT_FOUND = "权限配置不存在";
    private static final String MSG_BOT_TYPE_REQUIRED = "botType 不能为空";
    private static final String MSG_DEMO_USERS_INITIALIZED = "演示用户初始化完成";

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final AiUserMapper userMapper;
    private final BotPermissionMapper botPermissionMapper;

    @Value("${jwt.access-expiration-ms:${jwt.expiration-ms:900000}}")
    private long accessExpirationMs = 900_000L;

    @Value("${jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs = 604_800_000L;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            return Result.fail(400, MSG_USERNAME_PASSWORD_REQUIRED);
        }

        AiUser user = userMapper.selectByUsernameAndEnabled(username);
        if (user == null) {
            log.warn("登录失败，用户不存在或已禁用: username={}", username);
            return Result.fail(401, MSG_INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("登录失败，密码错误: username={}", username);
            return Result.fail(401, MSG_INVALID_CREDENTIALS);
        }

        String roles = user.getRoles() != null ? user.getRoles() : "ROLE_USER";
        String department = user.getDepartment() != null ? user.getDepartment() : "";
        String userId = user.getId();

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        Map<String, Object> claims = buildUserClaims(username, department, roles, userId);
        Map<String, Object> tokenPayload = buildTokenPayload(username, department, roles, userId, claims);
        log.info("登录成功: username={}, roles={}", username, roles);
        return Result.ok(tokenPayload);
    }

    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = "Authorization", defaultValue = "") String authorization,
            @RequestBody(required = false) Map<String, String> body) {
        if (authorization.startsWith("Bearer ")) {
            blacklistToken(authorization.substring(7));
        }
        if (body != null) {
            blacklistToken(body.get("refreshToken"));
        }
        return Result.ok();
    }

    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.getOrDefault("refreshToken", body.get("token"));
        if (refreshToken == null || !jwtUtil.validateToken(refreshToken)) {
            return Result.fail(401, MSG_TOKEN_INVALID_OR_EXPIRED);
        }
        if (!"refresh".equals(jwtUtil.getTokenType(refreshToken))) {
            return Result.fail(401, MSG_TOKEN_INVALID_OR_EXPIRED);
        }
        if (isTokenBlacklisted(refreshToken)) {
            return Result.fail(401, MSG_TOKEN_REVOKED);
        }

        String subject = jwtUtil.getSubject(refreshToken);
        String department = stringify(jwtUtil.getClaim(refreshToken, "department"));
        String roles = stringify(jwtUtil.getClaim(refreshToken, "roles"));
        String userId = stringify(jwtUtil.getClaim(refreshToken, "userId"));
        Map<String, Object> claims = buildUserClaims(subject, department, roles, userId);

        blacklistToken(refreshToken);
        return Result.ok(buildTokenPayload(subject, department, roles, userId, claims));
    }

    @GetMapping("/validate")
    public Result<Map<String, Object>> validate(
            @RequestHeader(value = "Authorization", defaultValue = "") String authorization) {
        if (!authorization.startsWith("Bearer ")) {
            return Result.fail(401, MSG_AUTH_HEADER_MISSING);
        }

        String token = authorization.substring(7);
        if (isTokenBlacklisted(token)) {
            return Result.fail(401, MSG_TOKEN_REVOKED);
        }
        if (!jwtUtil.validateToken(token)) {
            return Result.fail(401, MSG_TOKEN_INVALID);
        }

        Object userIdClaim = jwtUtil.getClaim(token, "userId");
        String userId = userIdClaim != null ? userIdClaim.toString() : jwtUtil.getSubject(token);
        Object roles = jwtUtil.getClaim(token, "roles");
        Object dept = jwtUtil.getClaim(token, "department");

        return Result.ok(Map.of(
                "userId", userId != null ? userId : "",
                "roles", roles != null ? roles : "",
                "department", dept != null ? dept : ""
        ));
    }

    @GetMapping("/my-bots")
    public Result<List<BotPermission>> myBots(HttpServletRequest request, Authentication authentication) {
        String roles = authentication != null
                ? authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .reduce((left, right) -> left + "," + right)
                .orElse((String) request.getAttribute("X-Roles"))
                : (String) request.getAttribute("X-Roles");
        String department = (String) request.getAttribute("X-Department");

        List<BotPermission> all = botPermissionMapper.selectList(null);
        List<BotPermission> available = all.stream()
                .filter(BotPermission::isEnabled)
                .filter(permission -> {
                    if (roles != null && roles.contains("ROLE_ADMIN")) {
                        return true;
                    }
                    if (permission.getAllowedRoles() != null
                            && !permission.getAllowedRoles().isBlank()
                            && roles != null) {
                        for (String role : roles.split(",")) {
                            if (permission.getAllowedRoles().contains(role.trim())) {
                                return true;
                            }
                        }
                    }
                    if (permission.getAllowedDepartments() != null
                            && !permission.getAllowedDepartments().isBlank()
                            && department != null) {
                        return permission.getAllowedDepartments().contains(department.trim());
                    }
                    return false;
                })
                .toList();

        return Result.ok(available);
    }

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
        String[] departments = {
                "系统管理",
                "研发中心",
                "销售部",
                "人力资源部",
                "财务部"
        };

        for (int i = 0; i < users.length; i++) {
            if (userMapper.selectByUsername(users[i]) == null) {
                userMapper.insert(AiUser.builder()
                        .id(UUID.randomUUID().toString())
                        .username(users[i])
                        .passwordHash(passwordEncoder.encode("admin123"))
                        .roles(roles[i])
                        .department(departments[i])
                        .employeeId("EMP00" + (i + 1))
                        .build());
            }
        }
        return Result.ok(MSG_DEMO_USERS_INITIALIZED);
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<List<AiUser>> listUsers() {
        List<AiUser> users = userMapper.selectList(null);
        users.forEach(user -> user.setPasswordHash(null));
        return Result.ok(users);
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<AiUser> getUser(@PathVariable(name = "id") String id) {
        AiUser user = userMapper.selectById(id);
        if (user == null) {
            return Result.fail(404, MSG_USER_NOT_FOUND);
        }
        user.setPasswordHash(null);
        return Result.ok(user);
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<AiUser> createUser(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Result.fail(400, MSG_USERNAME_PASSWORD_REQUIRED);
        }
        if (userMapper.selectByUsername(username) != null) {
            return Result.fail(400, MSG_USERNAME_EXISTS);
        }

        AiUser user = AiUser.builder()
                .id(username)
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

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<AiUser> updateUser(@PathVariable(name = "id") String id, @RequestBody Map<String, String> body) {
        AiUser user = userMapper.selectById(id);
        if (user == null) {
            return Result.fail(404, MSG_USER_NOT_FOUND);
        }

        if (body.containsKey("department")) {
            user.setDepartment(body.get("department"));
        }
        if (body.containsKey("employeeId")) {
            user.setEmployeeId(body.get("employeeId"));
        }
        if (body.containsKey("roles")) {
            user.setRoles(body.get("roles"));
        }
        if (body.containsKey("enabled")) {
            user.setEnabled(Boolean.parseBoolean(body.get("enabled")));
        }

        String password = body.get("password");
        if (password != null && !password.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }

        userMapper.updateById(user);
        user.setPasswordHash(null);
        log.info("更新用户: id={}, username={}", id, user.getUsername());
        return Result.ok(user);
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Void> deleteUser(@PathVariable(name = "id") String id) {
        AiUser user = userMapper.selectById(id);
        if (user == null) {
            return Result.fail(404, MSG_USER_NOT_FOUND);
        }
        userMapper.deleteById(id);
        log.info("删除用户: id={}, username={}", id, user.getUsername());
        return Result.ok();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<List<BotPermission>> listPermissions() {
        return Result.ok(botPermissionMapper.selectList(null));
    }

    @GetMapping("/permissions/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<BotPermission> getPermission(@PathVariable(name = "id") String id) {
        BotPermission permission = botPermissionMapper.selectById(id);
        if (permission == null) {
            return Result.fail(404, MSG_PERMISSION_NOT_FOUND);
        }
        return Result.ok(permission);
    }

    @PostMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<BotPermission> createPermission(@RequestBody Map<String, Object> body) {
        String botType = (String) body.get("botType");
        if (botType == null || botType.isBlank()) {
            return Result.fail(400, MSG_BOT_TYPE_REQUIRED);
        }

        BotPermission existing = botPermissionMapper.selectByBotTypeAndEnabled(botType);
        if (existing != null) {
            return Result.fail(400, "Bot 类型 '" + botType + "' 的权限配置已存在");
        }

        BotPermission permission = BotPermission.builder()
                .botType(botType)
                .allowedRoles(body.containsKey("allowedRoles") ? (String) body.get("allowedRoles") : "ROLE_ADMIN")
                .allowedDepartments(body.containsKey("allowedDepartments") ? (String) body.get("allowedDepartments") : null)
                .dataScope(body.containsKey("dataScope") ? (String) body.get("dataScope") : "DEPARTMENT")
                .allowedOperations(body.containsKey("allowedOperations") ? (String) body.get("allowedOperations") : "READ,WRITE")
                .dailyTokenLimit(body.containsKey("dailyTokenLimit")
                        ? ((Number) body.get("dailyTokenLimit")).intValue()
                        : 100000)
                .enabled(body.containsKey("enabled") ? (Boolean) body.get("enabled") : true)
                .build();

        botPermissionMapper.insert(permission);
        log.info("新增 Bot 权限: botType={}, allowedRoles={}", botType, permission.getAllowedRoles());
        return Result.ok(permission);
    }

    @PutMapping("/permissions/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<BotPermission> updatePermission(
            @PathVariable(name = "id") String id,
            @RequestBody Map<String, Object> body) {
        BotPermission permission = botPermissionMapper.selectById(id);
        if (permission == null) {
            return Result.fail(404, MSG_PERMISSION_NOT_FOUND);
        }

        if (body.containsKey("allowedRoles")) {
            permission.setAllowedRoles((String) body.get("allowedRoles"));
        }
        if (body.containsKey("allowedDepartments")) {
            permission.setAllowedDepartments((String) body.get("allowedDepartments"));
        }
        if (body.containsKey("dataScope")) {
            permission.setDataScope((String) body.get("dataScope"));
        }
        if (body.containsKey("allowedOperations")) {
            permission.setAllowedOperations((String) body.get("allowedOperations"));
        }
        if (body.containsKey("dailyTokenLimit")) {
            permission.setDailyTokenLimit(((Number) body.get("dailyTokenLimit")).intValue());
        }
        if (body.containsKey("enabled")) {
            permission.setEnabled((Boolean) body.get("enabled"));
        }

        botPermissionMapper.updateById(permission);
        log.info("更新 Bot 权限: id={}, botType={}", id, permission.getBotType());
        return Result.ok(permission);
    }

    @DeleteMapping("/permissions/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Void> deletePermission(@PathVariable(name = "id") String id) {
        BotPermission permission = botPermissionMapper.selectById(id);
        if (permission == null) {
            return Result.fail(404, MSG_PERMISSION_NOT_FOUND);
        }
        botPermissionMapper.deleteById(id);
        log.info("删除 Bot 权限: id={}, botType={}", id, permission.getBotType());
        return Result.ok();
    }

    private Map<String, Object> buildUserClaims(String username, String department, String roles, String userId) {
        return Map.of(
                "username", username,
                "department", department != null ? department : "",
                "roles", roles != null ? roles : "",
                "userId", userId != null ? userId : ""
        );
    }

    private Map<String, Object> buildTokenPayload(String username,
                                                  String department,
                                                  String roles,
                                                  String userId,
                                                  Map<String, Object> claims) {
        String accessToken = jwtUtil.generateToken(
                username, claims, accessExpirationMs, "access", UUID.randomUUID().toString());
        String refreshToken = jwtUtil.generateToken(
                username, claims, refreshExpirationMs, "refresh", UUID.randomUUID().toString());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", accessToken);
        payload.put("refreshToken", refreshToken);
        payload.put("tokenType", "Bearer");
        payload.put("expiresIn", accessExpirationMs / 1000);
        payload.put("refreshExpiresIn", refreshExpirationMs / 1000);
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("roles", roles);
        payload.put("department", department);
        return payload;
    }

    private boolean isTokenBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + token))) {
            return true;
        }
        if (!jwtUtil.validateToken(token)) {
            return false;
        }
        String jti = jwtUtil.getJti(token);
        return jti != null && Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_JTI_BLACKLIST_PREFIX + jti));
    }

    private void blacklistToken(String token) {
        if (token == null || token.isBlank() || !jwtUtil.validateToken(token)) {
            return;
        }
        long ttlMillis = jwtUtil.parseToken(token).getExpiration().getTime() - System.currentTimeMillis();
        long ttlSeconds = Math.max(1, TimeUnit.MILLISECONDS.toSeconds(ttlMillis));
        redisTemplate.opsForValue().set(TOKEN_BLACKLIST_PREFIX + token, "1", ttlSeconds, TimeUnit.SECONDS);
        String jti = jwtUtil.getJti(token);
        if (jti != null && !jti.isBlank()) {
            redisTemplate.opsForValue().set(TOKEN_JTI_BLACKLIST_PREFIX + jti, "1", ttlSeconds, TimeUnit.SECONDS);
        }
        log.info("Token 已加入黑名单: tokenType={}, jti={}", jwtUtil.getTokenType(token), jti);
    }

    private String stringify(Object value) {
        return value != null ? value.toString() : "";
    }
}
