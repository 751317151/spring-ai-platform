package com.huah.ai.platform.auth.controller;

import com.huah.ai.platform.auth.config.SecurityConfig;
import com.huah.ai.platform.auth.mapper.AiUserMapper;
import com.huah.ai.platform.auth.mapper.BotPermissionMapper;
import com.huah.ai.platform.auth.model.AiUser;
import com.huah.ai.platform.common.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, CorsConfig.class, AuthControllerAdminAuthorizationTest.MethodSecurityTestConfig.class})
@TestPropertySource(properties = {
        "jwt.secret=enterprise-ai-platform-secret-key-minimum-256-bits!!",
        "jwt.expiration-ms=86400000",
        "app.cors.allowed-origins=http://localhost:5173"
})
class AuthControllerAdminAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private AiUserMapper userMapper;

    @MockBean
    private BotPermissionMapper botPermissionMapper;

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void listUsersAllowsAdmin() throws Exception {
        when(userMapper.selectList(isNull())).thenReturn(List.of(
                AiUser.builder().id("u-1").username("admin").roles("ROLE_ADMIN").build()
        ));

        mockMvc.perform(get("/api/v1/auth/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].username").value("admin"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void listUsersRejectsNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/auth/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }
}
