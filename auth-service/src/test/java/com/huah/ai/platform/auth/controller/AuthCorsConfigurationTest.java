package com.huah.ai.platform.auth.controller;

import com.huah.ai.platform.auth.config.SecurityConfig;
import com.huah.ai.platform.auth.mapper.AiBotPermissionRoleMapper;
import com.huah.ai.platform.auth.mapper.AiRoleMapper;
import com.huah.ai.platform.auth.mapper.AiRoleTokenLimitMapper;
import com.huah.ai.platform.auth.mapper.AiUserMapper;
import com.huah.ai.platform.auth.mapper.AiUserRoleMapper;
import com.huah.ai.platform.auth.mapper.AiUserTokenLimitMapper;
import com.huah.ai.platform.auth.mapper.BotPermissionMapper;
import com.huah.ai.platform.auth.service.AuthAdminService;
import com.huah.ai.platform.auth.service.AuthQuotaAdminService;
import com.huah.ai.platform.auth.service.AuthRoleService;
import com.huah.ai.platform.auth.service.AuthTokenService;
import com.huah.ai.platform.common.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, CorsConfig.class, AuthCorsConfigurationTest.MethodSecurityTestConfig.class})
@TestPropertySource(properties = {
        "jwt.secret=enterprise-ai-platform-secret-key-minimum-256-bits!!",
        "jwt.expiration-ms=86400000",
        "app.cors.allowed-origins=http://localhost:5173,http://127.0.0.1:5173"
})
class AuthCorsConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private AiUserMapper userMapper;

    @MockBean
    private AiRoleMapper aiRoleMapper;

    @MockBean
    private AiRoleTokenLimitMapper aiRoleTokenLimitMapper;

    @MockBean
    private AiUserRoleMapper aiUserRoleMapper;

    @MockBean
    private AiUserTokenLimitMapper aiUserTokenLimitMapper;

    @MockBean
    private BotPermissionMapper botPermissionMapper;

    @MockBean
    private AiBotPermissionRoleMapper aiBotPermissionRoleMapper;

    @MockBean
    private AuthTokenService authTokenService;

    @MockBean
    private AuthAdminService authAdminService;

    @MockBean
    private AuthQuotaAdminService authQuotaAdminService;

    @MockBean
    private AuthRoleService authRoleService;

    @Test
    void shouldAllowConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/validate")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void shouldRejectUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/validate")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }
}
