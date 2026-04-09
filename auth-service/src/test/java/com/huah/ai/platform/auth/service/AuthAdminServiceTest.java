package com.huah.ai.platform.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.huah.ai.platform.auth.mapper.AiBotPermissionRoleMapper;
import com.huah.ai.platform.auth.mapper.AiRoleMapper;
import com.huah.ai.platform.auth.mapper.AiRoleTokenLimitMapper;
import com.huah.ai.platform.auth.mapper.AiUserMapper;
import com.huah.ai.platform.auth.mapper.AiUserRoleMapper;
import com.huah.ai.platform.auth.mapper.AiUserTokenLimitMapper;
import com.huah.ai.platform.auth.mapper.BotPermissionMapper;
import com.huah.ai.platform.auth.model.AiUserEntity;
import com.huah.ai.platform.common.dto.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthAdminServiceTest {

    private final AiRoleMapper roleMapper = mock(AiRoleMapper.class);
    private final AiUserMapper userMapper = mock(AiUserMapper.class);
    private final AiUserRoleMapper userRoleMapper = mock(AiUserRoleMapper.class);
    private final AiUserTokenLimitMapper userTokenLimitMapper = mock(AiUserTokenLimitMapper.class);
    private final BotPermissionMapper botPermissionMapper = mock(BotPermissionMapper.class);
    private final AiBotPermissionRoleMapper botPermissionRoleMapper = mock(AiBotPermissionRoleMapper.class);
    private final AiRoleTokenLimitMapper roleTokenLimitMapper = mock(AiRoleTokenLimitMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthViewAssembler authViewAssembler = new AuthViewAssembler();
    private final AuthRoleService authRoleService = mock(AuthRoleService.class);

    private final AuthAdminService service = new AuthAdminService(
            roleMapper,
            userMapper,
            userRoleMapper,
            userTokenLimitMapper,
            botPermissionMapper,
            botPermissionRoleMapper,
            roleTokenLimitMapper,
            passwordEncoder,
            authViewAssembler,
            authRoleService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deleteUserRejectsCurrentAuthenticatedUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null));
        when(userMapper.selectByUserId("admin")).thenReturn(AiUserEntity.builder()
                .userId("admin")
                .username("管理员")
                .enabled(true)
                .roles("ROLE_ADMIN")
                .build());

        Result<Void> result = service.deleteUser("admin");

        assertEquals(400, result.getCode());
        assertEquals("不能删除当前登录用户", result.getMessage());
        verifyNoInteractions(userTokenLimitMapper);
    }

    @Test
    void updateUserRejectsRemovingLastAdmin() {
        when(userMapper.selectByUserId("admin")).thenReturn(AiUserEntity.builder()
                .userId("admin")
                .username("管理员")
                .enabled(true)
                .roles("ROLE_ADMIN")
                .build());
        when(userRoleMapper.countEnabledUsersByRoleName("ROLE_ADMIN")).thenReturn(1);

        var request = new com.huah.ai.platform.auth.dto.UserUpsertRequest();
        request.setEnabled("false");

        Result<?> result = service.updateUser("admin", request);

        assertEquals(400, result.getCode());
        assertEquals("至少需保留一个启用状态的管理员", result.getMessage());
        verifyNoInteractions(authRoleService);
    }
}
