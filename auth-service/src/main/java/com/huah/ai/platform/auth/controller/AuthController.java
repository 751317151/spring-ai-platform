package com.huah.ai.platform.auth.controller;

import com.huah.ai.platform.auth.model.AiUser;
import com.huah.ai.platform.auth.repository.AiUserRepository;
import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 认证接口 — JWT 登录 / 登出 / 刷新 / 验证
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final AiUserRepository userRepository;

    private static final String TOKEN_BLACKLIST_PREFIX = "ai:token:blacklist:";

    /**
     * 登录 — 支持数据库用户 + 演示 fallback（admin/admin123）
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            return Result.fail(400, "用户名和密码不能为空");
        }

        // 1. 尝试从数据库查询用户
        AiUser user = userRepository.findByUsernameAndEnabledTrue(username).orElse(null);

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
            userRepository.save(user);
        } else {
            // 演示 fallback — admin/admin123 直接通过
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
     * 登出 — Token 加入 Redis 黑名单
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
     * 初始化演示用户（首次部署调用一次）
     */
    @PostMapping("/init-demo-users")
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
            if (userRepository.findByUsername(users[i]).isEmpty()) {
                userRepository.save(AiUser.builder()
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
}
