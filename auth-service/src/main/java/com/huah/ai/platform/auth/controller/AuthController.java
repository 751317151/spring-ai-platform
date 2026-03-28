package com.huah.ai.platform.auth.controller;

import com.huah.ai.platform.auth.dto.BotPermissionUpsertRequest;
import com.huah.ai.platform.auth.dto.LoginRequest;
import com.huah.ai.platform.auth.dto.LogoutRequest;
import com.huah.ai.platform.auth.dto.RefreshTokenRequest;
import com.huah.ai.platform.auth.dto.TokenResponse;
import com.huah.ai.platform.auth.dto.TokenValidationResponse;
import com.huah.ai.platform.auth.dto.UserUpsertRequest;
import com.huah.ai.platform.auth.mapper.AiUserMapper;
import com.huah.ai.platform.auth.mapper.BotPermissionMapper;
import com.huah.ai.platform.auth.model.AiUser;
import com.huah.ai.platform.auth.model.BotPermission;
import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String TOKEN_BLACKLIST_PREFIX = "ai:token:blacklist:";
    private static final String TOKEN_JTI_BLACKLIST_PREFIX = "ai:token:blacklist:jti:";

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
    public Result<TokenResponse> login(@RequestBody LoginRequest request) {
        if (isBlank(request.getUserId()) || isBlank(request.getPassword())) {
            return Result.fail(400, "userId 和密码不能为空");
        }

        AiUser user = userMapper.selectByUserIdAndEnabled(request.getUserId());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed, userId={}", request.getUserId());
            return Result.fail(401, "userId 或密码错误");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        String roles = defaultString(user.getRoles(), "ROLE_USER");
        String department = defaultString(user.getDepartment(), "");
        TokenResponse payload = buildTokenPayload(user.getUserId(), user.getUsername(), department, roles);
        log.info("Login success: userId={}, roles={}", user.getUserId(), roles);
        return Result.ok(payload);
    }

    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = "Authorization", defaultValue = "") String authorization,
            @RequestBody(required = false) LogoutRequest request) {
        if (authorization.startsWith("Bearer ")) {
            blacklistToken(authorization.substring(7));
        }
        if (request != null) {
            blacklistToken(request.getRefreshToken());
        }
        return Result.ok();
    }

    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken() != null ? request.getRefreshToken() : request.getToken();
        if (isBlank(refreshToken) || !jwtUtil.validateToken(refreshToken) || !"refresh".equals(jwtUtil.getTokenType(refreshToken))) {
            return Result.fail(401, "Token 无效或已过期");
        }
        if (isTokenBlacklisted(refreshToken)) {
            return Result.fail(401, "Token 已失效");
        }

        String userId = jwtUtil.getSubject(refreshToken);
        String username = stringify(jwtUtil.getClaim(refreshToken, "username"));
        String department = stringify(jwtUtil.getClaim(refreshToken, "department"));
        String roles = stringify(jwtUtil.getClaim(refreshToken, "roles"));

        blacklistToken(refreshToken);
        return Result.ok(buildTokenPayload(userId, username, department, roles));
    }

    @GetMapping("/validate")
    public Result<TokenValidationResponse> validate(
            @RequestHeader(value = "Authorization", defaultValue = "") String authorization) {
        if (!authorization.startsWith("Bearer ")) {
            return Result.fail(401, "Authorization header 缺失");
        }

        String token = authorization.substring(7);
        if (isTokenBlacklisted(token)) {
            return Result.fail(401, "Token 已失效");
        }
        if (!jwtUtil.validateToken(token)) {
            return Result.fail(401, "Token 无效");
        }

        return Result.ok(TokenValidationResponse.builder()
                .userId(stringify(jwtUtil.getClaim(token, "userId")))
                .roles(stringify(jwtUtil.getClaim(token, "roles")))
                .department(stringify(jwtUtil.getClaim(token, "department")))
                .build());
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

        List<BotPermission> available = botPermissionMapper.selectList(null).stream()
                .filter(BotPermission::isEnabled)
                .filter(permission -> hasBotAccess(permission, roles, department))
                .toList();

        return Result.ok(available);
    }

    @PostMapping("/init-demo-users")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<String> initDemoUsers() {
        String[] userIds = {"admin", "rd_user", "sales_user", "hr_user", "finance_user"};
        String[] usernames = {"管理员", "研发用户", "销售用户", "HR用户", "财务用户"};
        String[] roles = {
                "ROLE_ADMIN,ROLE_RD,ROLE_SALES,ROLE_HR,ROLE_USER",
                "ROLE_RD,ROLE_USER",
                "ROLE_SALES,ROLE_USER",
                "ROLE_HR,ROLE_USER",
                "ROLE_FINANCE,ROLE_USER"
        };
        String[] departments = {"系统管理", "研发中心", "销售部", "人力资源部", "财务部"};

        for (int i = 0; i < userIds.length; i++) {
            if (userMapper.selectByUserId(userIds[i]) == null) {
                userMapper.insert(AiUser.builder()
                        .userId(userIds[i])
                        .username(usernames[i])
                        .passwordHash(passwordEncoder.encode("admin123"))
                        .roles(roles[i])
                        .department(departments[i])
                        .employeeId("EMP00" + (i + 1))
                        .build());
            }
        }
        return Result.ok("演示用户初始化完成");
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<List<AiUser>> listUsers() {
        List<AiUser> users = userMapper.selectList(null);
        users.forEach(this::sanitizeUser);
        return Result.ok(users);
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<AiUser> getUser(@PathVariable(name = "id") String id) {
        AiUser user = userMapper.selectById(id);
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }
        sanitizeUser(user);
        return Result.ok(user);
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<AiUser> createUser(@RequestBody UserUpsertRequest request) {
        if (isBlank(request.getUserId()) || isBlank(request.getPassword())) {
            return Result.fail(400, "userId 和密码不能为空");
        }
        if (userMapper.selectByUserId(request.getUserId()) != null) {
            return Result.fail(400, "userId 已存在");
        }
        if (!isBlank(request.getUsername()) && userMapper.selectByUsername(request.getUsername()) != null) {
            return Result.fail(400, "用户名已存在");
        }

        AiUser user = AiUser.builder()
                .userId(request.getUserId())
                .username(defaultString(request.getUsername(), request.getUserId()))
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .department(defaultString(request.getDepartment(), ""))
                .employeeId(defaultString(request.getEmployeeId(), ""))
                .roles(defaultString(request.getRoles(), "ROLE_USER"))
                .enabled(parseEnabled(request.getEnabled(), true))
                .build();
        userMapper.insert(user);
        sanitizeUser(user);
        log.info("Create user: userId={}", request.getUserId());
        return Result.ok(user);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<AiUser> updateUser(@PathVariable(name = "id") String id, @RequestBody UserUpsertRequest request) {
        AiUser user = userMapper.selectById(id);
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }

        if (request.getUsername() != null) {
            user.setUsername(request.getUsername());
        }
        if (request.getDepartment() != null) {
            user.setDepartment(request.getDepartment());
        }
        if (request.getEmployeeId() != null) {
            user.setEmployeeId(request.getEmployeeId());
        }
        if (request.getRoles() != null) {
            user.setRoles(request.getRoles());
        }
        if (request.getEnabled() != null) {
            user.setEnabled(parseEnabled(request.getEnabled(), user.isEnabled()));
        }
        if (!isBlank(request.getPassword())) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        userMapper.updateById(user);
        sanitizeUser(user);
        log.info("Update user: userId={}", id);
        return Result.ok(user);
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Void> deleteUser(@PathVariable(name = "id") String id) {
        AiUser user = userMapper.selectById(id);
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }
        userMapper.deleteById(id);
        log.info("Delete user: userId={}", id);
        return Result.ok();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<List<BotPermission>> listPermissions() {
        return Result.ok(botPermissionMapper.selectList(null));
    }

    @GetMapping("/permissions/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<BotPermission> getPermission(@PathVariable(name = "id") Long id) {
        BotPermission permission = botPermissionMapper.selectById(id);
        if (permission == null) {
            return Result.fail(404, "权限配置不存在");
        }
        return Result.ok(permission);
    }

    @PostMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<BotPermission> createPermission(@RequestBody BotPermissionUpsertRequest request) {
        if (isBlank(request.getBotType())) {
            return Result.fail(400, "botType 不能为空");
        }

        BotPermission existing = botPermissionMapper.selectByBotTypeAndEnabled(request.getBotType());
        if (existing != null) {
            return Result.fail(400, "Bot 类型对应的权限配置已存在");
        }

        BotPermission permission = BotPermission.builder()
                .botType(request.getBotType())
                .allowedRoles(defaultString(request.getAllowedRoles(), "ROLE_ADMIN"))
                .allowedDepartments(request.getAllowedDepartments())
                .dataScope(defaultString(request.getDataScope(), "DEPARTMENT"))
                .allowedOperations(defaultString(request.getAllowedOperations(), "READ,WRITE"))
                .dailyTokenLimit(request.getDailyTokenLimit() != null ? request.getDailyTokenLimit() : 100000)
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();

        botPermissionMapper.insert(permission);
        log.info("Create bot permission: botType={}, allowedRoles={}", permission.getBotType(), permission.getAllowedRoles());
        return Result.ok(permission);
    }

    @PutMapping("/permissions/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<BotPermission> updatePermission(
            @PathVariable(name = "id") Long id,
            @RequestBody BotPermissionUpsertRequest request) {
        BotPermission permission = botPermissionMapper.selectById(id);
        if (permission == null) {
            return Result.fail(404, "权限配置不存在");
        }

        if (request.getAllowedRoles() != null) {
            permission.setAllowedRoles(request.getAllowedRoles());
        }
        if (request.getAllowedDepartments() != null) {
            permission.setAllowedDepartments(request.getAllowedDepartments());
        }
        if (request.getDataScope() != null) {
            permission.setDataScope(request.getDataScope());
        }
        if (request.getAllowedOperations() != null) {
            permission.setAllowedOperations(request.getAllowedOperations());
        }
        if (request.getDailyTokenLimit() != null) {
            permission.setDailyTokenLimit(request.getDailyTokenLimit());
        }
        if (request.getEnabled() != null) {
            permission.setEnabled(request.getEnabled());
        }

        botPermissionMapper.updateById(permission);
        log.info("Update bot permission: id={}, botType={}", id, permission.getBotType());
        return Result.ok(permission);
    }

    @DeleteMapping("/permissions/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Void> deletePermission(@PathVariable(name = "id") Long id) {
        BotPermission permission = botPermissionMapper.selectById(id);
        if (permission == null) {
            return Result.fail(404, "权限配置不存在");
        }
        botPermissionMapper.deleteById(id);
        log.info("Delete bot permission: id={}, botType={}", id, permission.getBotType());
        return Result.ok();
    }

    private TokenResponse buildTokenPayload(String userId, String username, String department, String roles) {
        Map<String, Object> claims = Map.of(
                "userId", userId,
                "username", defaultString(username, userId),
                "department", defaultString(department, ""),
                "roles", defaultString(roles, ""));

        String accessToken = jwtUtil.generateToken(userId, claims, accessExpirationMs, "access", UUID.randomUUID().toString());
        String refreshToken = jwtUtil.generateToken(userId, claims, refreshExpirationMs, "refresh", UUID.randomUUID().toString());

        return TokenResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessExpirationMs / 1000)
                .refreshExpiresIn(refreshExpirationMs / 1000)
                .userId(userId)
                .username(defaultString(username, userId))
                .roles(roles)
                .department(department)
                .build();
    }

    private boolean hasBotAccess(BotPermission permission, String roles, String department) {
        if (roles != null && roles.contains("ROLE_ADMIN")) {
            return true;
        }
        if (!isBlank(permission.getAllowedRoles()) && roles != null) {
            for (String role : roles.split(",")) {
                if (permission.getAllowedRoles().contains(role.trim())) {
                    return true;
                }
            }
        }
        return !isBlank(permission.getAllowedDepartments())
                && department != null
                && permission.getAllowedDepartments().contains(department.trim());
    }

    private void sanitizeUser(AiUser user) {
        user.setPasswordHash(null);
    }

    private boolean isTokenBlacklisted(String token) {
        if (isBlank(token)) {
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
        if (isBlank(token) || !jwtUtil.validateToken(token)) {
            return;
        }
        long ttlMillis = jwtUtil.parseToken(token).getExpiration().getTime() - System.currentTimeMillis();
        long ttlSeconds = Math.max(1, TimeUnit.MILLISECONDS.toSeconds(ttlMillis));
        redisTemplate.opsForValue().set(TOKEN_BLACKLIST_PREFIX + token, "1", ttlSeconds, TimeUnit.SECONDS);
        String jti = jwtUtil.getJti(token);
        if (!isBlank(jti)) {
            redisTemplate.opsForValue().set(TOKEN_JTI_BLACKLIST_PREFIX + jti, "1", ttlSeconds, TimeUnit.SECONDS);
        }
        log.info("Token blacklisted: tokenType={}, jti={}", jwtUtil.getTokenType(token), jti);
    }

    private Boolean parseEnabled(String enabled, Boolean defaultValue) {
        return enabled != null ? Boolean.parseBoolean(enabled) : defaultValue;
    }

    private String stringify(Object value) {
        return value != null ? value.toString() : "";
    }

    private String defaultString(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
