package com.huah.ai.platform.auth.controller;

import com.huah.ai.platform.auth.dto.LoginRequest;
import com.huah.ai.platform.auth.dto.LogoutRequest;
import com.huah.ai.platform.auth.dto.RefreshTokenRequest;
import com.huah.ai.platform.auth.dto.TokenResponse;
import com.huah.ai.platform.auth.dto.TokenValidationResponse;
import com.huah.ai.platform.auth.mapper.AiUserMapper;
import com.huah.ai.platform.auth.mapper.BotPermissionMapper;
import com.huah.ai.platform.auth.model.AiUser;
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
    private AiUserMapper userMapper;

    @Mock
    private BotPermissionMapper botPermissionMapper;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        JwtUtil jwtUtil = new JwtUtil("enterprise-ai-platform-secret-key-minimum-256-bits!!", 900_000L);
        controller = new AuthController(jwtUtil, passwordEncoder, redisTemplate, userMapper, botPermissionMapper);
    }

    @Test
    void loginReturnsTokenPairForValidCredentials() {
        AiUser user = AiUser.builder()
                .id("user-1")
                .username("alice")
                .passwordHash("encoded-password")
                .department("研发中心")
                .roles("ROLE_USER")
                .build();

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("plain-password");

        when(userMapper.selectByUsernameAndEnabled("alice")).thenReturn(user);
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);

        Result<TokenResponse> result = controller.login(request);

        assertEquals(200, result.getCode());
        assertEquals("alice", result.getData().getUsername());
        assertEquals("user-1", result.getData().getUserId());
        assertEquals("Bearer", result.getData().getTokenType());
        assertNotNull(result.getData().getToken());
        assertNotNull(result.getData().getRefreshToken());
        verify(userMapper).updateById(any(AiUser.class));
    }

    @Test
    void loginRejectsInvalidPassword() {
        AiUser user = AiUser.builder()
                .id("user-1")
                .username("alice")
                .passwordHash("encoded-password")
                .build();

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("bad-password");

        when(userMapper.selectByUsernameAndEnabled("alice")).thenReturn(user);
        when(passwordEncoder.matches("bad-password", "encoded-password")).thenReturn(false);

        Result<TokenResponse> result = controller.login(request);

        assertEquals(401, result.getCode());
        assertEquals("用户名或密码错误", result.getMessage());
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
        assertEquals("Token 已失效", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void refreshReturnsRotatedTokenPair() {
        AiUser user = AiUser.builder()
                .id("user-1")
                .username("alice")
                .passwordHash("encoded-password")
                .department("研发中心")
                .roles("ROLE_USER")
                .build();
        when(userMapper.selectByUsernameAndEnabled("alice")).thenReturn(user);
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("alice");
        loginRequest.setPassword("plain-password");
        Result<TokenResponse> loginResult = controller.login(loginRequest);

        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken(loginResult.getData().getRefreshToken());
        Result<TokenResponse> result = controller.refresh(refreshRequest);

        assertEquals(200, result.getCode());
        assertNotNull(result.getData().getToken());
        assertNotNull(result.getData().getRefreshToken());
        verify(valueOperations).set(eq("ai:token:blacklist:" + refreshRequest.getRefreshToken()), eq("1"), anyLong(), eq(TimeUnit.SECONDS));
    }
}
