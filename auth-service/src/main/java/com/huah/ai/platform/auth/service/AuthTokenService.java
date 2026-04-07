package com.huah.ai.platform.auth.service;

import com.huah.ai.platform.auth.dto.BotPermissionResponse;
import com.huah.ai.platform.auth.dto.LoginRequest;
import com.huah.ai.platform.auth.dto.LogoutRequest;
import com.huah.ai.platform.auth.dto.RefreshTokenRequest;
import com.huah.ai.platform.auth.dto.TokenResponse;
import com.huah.ai.platform.auth.dto.TokenValidationResponse;
import com.huah.ai.platform.auth.mapper.AiUserMapper;
import com.huah.ai.platform.auth.mapper.BotPermissionMapper;
import com.huah.ai.platform.auth.model.AiUserEntity;
import com.huah.ai.platform.auth.model.BotPermissionEntity;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private static final String TOKEN_BLACKLIST_PREFIX = "ai:token:blacklist:";
    private static final String TOKEN_JTI_BLACKLIST_PREFIX = "ai:token:blacklist:jti:";
    private static final String MESSAGE_LOGIN_FIELDS_REQUIRED = "userId \u548c password \u4e0d\u80fd\u4e3a\u7a7a";
    private static final String MESSAGE_LOGIN_FAILED = "userId \u6216\u5bc6\u7801\u9519\u8bef";
    private static final String MESSAGE_REFRESH_INVALID = "Refresh Token \u65e0\u6548";
    private static final String MESSAGE_TOKEN_INVALID = "Token \u65e0\u6548";
    private static final String MESSAGE_TOKEN_EXPIRED = "Token \u5df2\u5931\u6548";
    private static final String MESSAGE_AUTH_HEADER_INVALID =
            "Authorization header \u683c\u5f0f\u9519\u8bef";

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final AiUserMapper userMapper;
    private final BotPermissionMapper botPermissionMapper;
    private final AuthViewAssembler authViewAssembler;
    private final AuthRoleService authRoleService;

    @Value("${jwt.access-expiration-ms:${jwt.expiration-ms:7200000}}")
    private long accessExpirationMs = 7_200_000L;

    @Value("${jwt.refresh-expiration-ms:2592000000}")
    private long refreshExpirationMs = 2_592_000_000L;

    public Result<TokenResponse> login(LoginRequest request) {
        if (isBlank(request.getUserId()) || isBlank(request.getPassword())) {
            return Result.fail(400, MESSAGE_LOGIN_FIELDS_REQUIRED);
        }

        AiUserEntity user = userMapper.selectByUserIdAndEnabled(request.getUserId());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed, userId={}", request.getUserId());
            return Result.fail(401, MESSAGE_LOGIN_FAILED);
        }

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        String roles = defaultString(user.getRoles(), "ROLE_USER");
        String department = defaultString(user.getDepartment(), "");
        String province = defaultString(user.getProvince(), "");
        String city = defaultString(user.getCity(), "");
        TokenResponse payload = buildTokenPayload(user.getUserId(), user.getUsername(), department, province, city, roles);
        log.info("Login success: userId={}, roles={}", user.getUserId(), roles);
        return Result.ok(payload);
    }

    public Result<Void> logout(String authorization, LogoutRequest request) {
        if (authorization.startsWith("Bearer ")) {
            blacklistToken(authorization.substring(7));
        }
        if (request != null) {
            blacklistToken(request.getRefreshToken());
        }
        return Result.ok();
    }

    public Result<TokenResponse> refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken() != null ? request.getRefreshToken() : request.getToken();
        if (isBlank(refreshToken)
                || !jwtUtil.validateToken(refreshToken)
                || !"refresh".equals(jwtUtil.getTokenType(refreshToken))) {
            return Result.fail(401, MESSAGE_REFRESH_INVALID);
        }
        if (isTokenBlacklisted(refreshToken)) {
            return Result.fail(401, MESSAGE_TOKEN_EXPIRED);
        }

        String userId = jwtUtil.getSubject(refreshToken);
        String username = stringify(jwtUtil.getClaim(refreshToken, "username"));
        String department = stringify(jwtUtil.getClaim(refreshToken, "department"));
        String province = stringify(jwtUtil.getClaim(refreshToken, "province"));
        String city = stringify(jwtUtil.getClaim(refreshToken, "city"));
        String roles = stringify(jwtUtil.getClaim(refreshToken, "roles"));

        blacklistToken(refreshToken);
        return Result.ok(buildTokenPayload(userId, username, department, province, city, roles));
    }

    public Result<TokenValidationResponse> validate(String authorization) {
        if (!authorization.startsWith("Bearer ")) {
            return Result.fail(401, MESSAGE_AUTH_HEADER_INVALID);
        }

        String token = authorization.substring(7);
        if (isTokenBlacklisted(token)) {
            return Result.fail(401, MESSAGE_TOKEN_EXPIRED);
        }
        if (!jwtUtil.validateToken(token)) {
            return Result.fail(401, MESSAGE_TOKEN_INVALID);
        }

        return Result.ok(TokenValidationResponse.builder()
                .userId(stringify(jwtUtil.getClaim(token, "userId")))
                .roles(stringify(jwtUtil.getClaim(token, "roles")))
                .department(stringify(jwtUtil.getClaim(token, "department")))
                .build());
    }

    public Result<List<BotPermissionResponse>> myBots(HttpServletRequest request, Authentication authentication) {
        String roles = authentication != null
                ? authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .reduce((left, right) -> left + "," + right)
                        .orElse((String) request.getAttribute("X-Roles"))
                : (String) request.getAttribute("X-Roles");
        String department = (String) request.getAttribute("X-Department");

        List<BotPermissionResponse> available = botPermissionMapper.selectList(null).stream()
                .peek(this::syncPermissionRolesView)
                .filter(BotPermissionEntity::isEnabled)
                .filter(permission -> hasBotAccess(permission, roles, department))
                .map(authViewAssembler::toPermissionResponse)
                .toList();

        return Result.ok(available);
    }

    private TokenResponse buildTokenPayload(
            String userId,
            String username,
            String department,
            String province,
            String city,
            String roles) {
        Map<String, Object> claims = Map.of(
                "userId", userId,
                "username", defaultString(username, userId),
                "department", defaultString(department, ""),
                "province", defaultString(province, ""),
                "city", defaultString(city, ""),
                "roles", defaultString(roles, ""));

        String accessToken =
                jwtUtil.generateToken(userId, claims, accessExpirationMs, "access", UUID.randomUUID().toString());
        String refreshToken =
                jwtUtil.generateToken(userId, claims, refreshExpirationMs, "refresh", UUID.randomUUID().toString());

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
                .province(province)
                .city(city)
                .build();
    }

    private boolean hasBotAccess(BotPermissionEntity permission, String roles, String department) {
        if (roles != null && roles.contains("ROLE_ADMIN")) {
            return true;
        }
        if (!isBlank(permission.getAllowedRoles()) && roles != null) {
            List<String> allowedRoles = List.of(permission.getAllowedRoles().split(","));
            for (String role : roles.split(",")) {
                if (allowedRoles.contains(role.trim())) {
                    return true;
                }
            }
        }
        if (!isBlank(permission.getAllowedDepartments()) && department != null) {
            List<String> allowedDepartments = List.of(permission.getAllowedDepartments().split(","));
            return allowedDepartments.contains(department.trim());
        }
        return false;
    }

    private void syncPermissionRolesView(BotPermissionEntity permission) {
        permission.setAllowedRoles(authRoleService.getPermissionRoleNamesCsv(permission.getId(), permission.getAllowedRoles()));
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
