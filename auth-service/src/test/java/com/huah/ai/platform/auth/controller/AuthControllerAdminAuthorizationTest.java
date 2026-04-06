package com.huah.ai.platform.auth.controller;

import com.huah.ai.platform.auth.config.SecurityConfig;
import com.huah.ai.platform.auth.dto.AuthUserResponse;
import com.huah.ai.platform.auth.dto.RoleOptionResponse;
import com.huah.ai.platform.auth.dto.RoleUsageResponse;
import com.huah.ai.platform.auth.mapper.AiBotPermissionRoleMapper;
import com.huah.ai.platform.auth.mapper.AiRoleMapper;
import com.huah.ai.platform.auth.mapper.AiUserMapper;
import com.huah.ai.platform.auth.mapper.AiUserRoleMapper;
import com.huah.ai.platform.auth.mapper.BotPermissionMapper;
import com.huah.ai.platform.auth.service.AuthAdminService;
import com.huah.ai.platform.auth.service.AuthRoleService;
import com.huah.ai.platform.auth.service.AuthTokenService;
import com.huah.ai.platform.common.config.CorsConfig;
import java.util.List;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private AiUserMapper aiUserMapper;

    @MockBean
    private AiRoleMapper aiRoleMapper;

    @MockBean
    private AiUserRoleMapper aiUserRoleMapper;

    @MockBean
    private BotPermissionMapper botPermissionMapper;

    @MockBean
    private AiBotPermissionRoleMapper aiBotPermissionRoleMapper;

    @MockBean
    private AuthTokenService authTokenService;

    @MockBean
    private AuthAdminService authAdminService;

    @MockBean
    private AuthRoleService authRoleService;

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void listUsersAllowsAdmin() throws Exception {
        when(authAdminService.listUsers()).thenReturn(com.huah.ai.platform.common.dto.Result.ok(List.of(
                AuthUserResponse.builder().userId("u-1").username("admin").roles("ROLE_ADMIN").enabled(true).build()
        )));

        mockMvc.perform(get("/api/v1/auth/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].username").value("admin"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void listRolesAllowsAdmin() throws Exception {
        when(authAdminService.listRoles()).thenReturn(com.huah.ai.platform.common.dto.Result.ok(List.of(
                RoleOptionResponse.builder().id(1001L).roleName("ROLE_ADMIN").description("系统管理员").build()
        )));

        mockMvc.perform(get("/api/v1/auth/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].roleName").value("ROLE_ADMIN"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void getRoleUsageAllowsAdmin() throws Exception {
        when(authAdminService.getRoleUsage(1001L)).thenReturn(com.huah.ai.platform.common.dto.Result.ok(
                RoleUsageResponse.builder()
                        .roleId(1001L)
                        .roleName("ROLE_ADMIN")
                        .userCount(1)
                        .permissionCount(2)
                        .userReferences(List.of("admin(管理员)"))
                        .permissionReferences(List.of("multi (#2007)", "mcp (#2012)"))
                        .build()));

        mockMvc.perform(get("/api/v1/auth/roles/1001/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userCount").value(1))
                .andExpect(jsonPath("$.data.permissionCount").value(2));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void createRoleAllowsAdmin() throws Exception {
        when(authAdminService.createRole(any())).thenReturn(com.huah.ai.platform.common.dto.Result.ok(
                RoleOptionResponse.builder().id(1999L).roleName("ROLE_SUPPORT").description("支持角色").build()));

        mockMvc.perform(post("/api/v1/auth/roles")
                        .contentType("application/json")
                        .content("{\"roleName\":\"ROLE_SUPPORT\",\"description\":\"支持角色\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.roleName").value("ROLE_SUPPORT"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void updateRoleAllowsAdmin() throws Exception {
        when(authAdminService.updateRole(eq(1001L), any())).thenReturn(com.huah.ai.platform.common.dto.Result.ok(
                RoleOptionResponse.builder().id(1001L).roleName("ROLE_ADMIN").description("系统管理员").build()));

        mockMvc.perform(put("/api/v1/auth/roles/1001")
                        .contentType("application/json")
                        .content("{\"roleName\":\"ROLE_ADMIN\",\"description\":\"系统管理员\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.roleName").value("ROLE_ADMIN"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void deleteRoleAllowsAdmin() throws Exception {
        when(authAdminService.deleteRole(1001L)).thenReturn(com.huah.ai.platform.common.dto.Result.ok());

        mockMvc.perform(delete("/api/v1/auth/roles/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void listUsersRejectsNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/auth/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void listRolesRejectsNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/auth/roles"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void getRoleUsageRejectsNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/auth/roles/1001/usage"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void createRoleRejectsNonAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/roles")
                        .contentType("application/json")
                        .content("{\"roleName\":\"ROLE_SUPPORT\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }
}
