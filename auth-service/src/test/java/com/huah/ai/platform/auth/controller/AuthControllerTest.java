package com.huah.ai.platform.auth.controller;

import com.huah.ai.platform.auth.dto.LoginRequest;
import com.huah.ai.platform.auth.dto.LogoutRequest;
import com.huah.ai.platform.auth.dto.RefreshTokenRequest;
import com.huah.ai.platform.auth.dto.TokenResponse;
import com.huah.ai.platform.auth.dto.TokenValidationResponse;
import com.huah.ai.platform.auth.model.AiUserEntity;
import com.huah.ai.platform.auth.service.AuthAdminService;
import com.huah.ai.platform.auth.service.AuthTokenService;
import com.huah.ai.platform.auth.service.AuthViewAssembler;
import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.common.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private com.huah.ai.platform.auth.mapper.AiUserMapper userMapper;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        JwtUtil jwtUtil = new JwtUtil("enterprise-ai-platform-secret-key-minimum-256-bits!!", 900_000L);
        AuthTokenService authTokenService = new AuthTokenService(
                jwtUtil,
                passwordEncoder,
                redisTemplate,
                userMapper,
                mock(com.huah.ai.platform.auth.mapper.BotPermissionMapper.class),
                new AuthViewAssembler()
        );
        controller = new AuthController(authTokenService, mock(AuthAdminService.class));
    }

    @Test
    void loginReturnsTokenPairForValidCredentials() {
        AiUserEntity user = AiUserEntity.builder()
                .userId("user-1")
                .username("alice")
                .passwordHash("encoded-password")
                .department("研发中心")
                .roles("ROLE_USER")
                .build();

        LoginRequest request = new LoginRequest();
        request.setUserId("user-1");
        request.setPassword("plain-password");

        when(userMapper.selectByUserIdAndEnabled("user-1")).thenReturn(user);
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);

        Result<TokenResponse> result = controller.login(request);

        assertEquals(200, result.getCode());
        assertEquals("alice", result.getData().getUsername());
        assertEquals("user-1", result.getData().getUserId());
        assertEquals("Bearer", result.getData().getTokenType());
        assertNotNull(result.getData().getToken());
        assertNotNull(result.getData().getRefreshToken());
        verify(userMapper).updateById(any(AiUserEntity.class));
    }

    @Test
    void loginRejectsInvalidPassword() {
        AiUserEntity user = AiUserEntity.builder()
                .userId("user-1")
                .username("alice")
                .passwordHash("encoded-password")
                .build();

        LoginRequest request = new LoginRequest();
        request.setUserId("user-1");
        request.setPassword("bad-password");

        when(userMapper.selectByUserIdAndEnabled("user-1")).thenReturn(user);
        when(passwordEncoder.matches("bad-password", "encoded-password")).thenReturn(false);

        Result<TokenResponse> result = controller.login(request);

        assertEquals(401, result.getCode());
        assertNotNull(result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void logoutAddsTokenToBlacklist() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String token = new JwtUtil("enterprise-ai-platform-secret-key-minimum-256-bits!!", 900_000L)
                .generateToken("alice", Map.of("username", "alice"));

        Result<Void> result = controller.logout("Bearer " + token, new LogoutRequest());

        assertEquals(200, result.getCode());
        verify(valueOperations).set(
                eq("ai:token:blacklist:" + token),
                eq("1"),
                anyLong(),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void validateRejectsBlacklistedToken() {
        when(redisTemplate.hasKey("ai:token:blacklist:blacklisted-token")).thenReturn(true);

        Result<TokenValidationResponse> result = controller.validate("Bearer blacklisted-token");

        assertEquals(401, result.getCode());
        assertNotNull(result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void refreshReturnsRotatedTokenPair() {
        AiUserEntity user = AiUserEntity.builder()
                .userId("user-1")
                .username("alice")
                .passwordHash("encoded-password")
                .department("研发中心")
                .roles("ROLE_USER")
                .build();
        when(userMapper.selectByUserIdAndEnabled("user-1")).thenReturn(user);
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUserId("user-1");
        loginRequest.setPassword("plain-password");
        Result<TokenResponse> loginResult = controller.login(loginRequest);

        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken(loginResult.getData().getRefreshToken());
        Result<TokenResponse> result = controller.refresh(refreshRequest);

        assertEquals(200, result.getCode());
        assertNotNull(result.getData().getToken());
        assertNotNull(result.getData().getRefreshToken());
        verify(valueOperations).set(
                eq("ai:token:blacklist:" + refreshRequest.getRefreshToken()),
                eq("1"),
                anyLong(),
                eq(TimeUnit.SECONDS)
        );
    }
}
