package com.huah.ai.platform.auth.service;

import com.huah.ai.platform.auth.dto.AuthUserResponse;
import com.huah.ai.platform.auth.dto.RoleOptionResponse;
import com.huah.ai.platform.auth.dto.RoleTokenLimitResponse;
import com.huah.ai.platform.auth.dto.UserTokenLimitResponse;
import com.huah.ai.platform.auth.model.AiRoleEntity;
import com.huah.ai.platform.auth.model.AiRoleTokenLimitEntity;
import com.huah.ai.platform.auth.model.AiUserEntity;
import com.huah.ai.platform.auth.model.AiUserTokenLimitEntity;
import org.springframework.stereotype.Component;

@Component
public class AuthViewAssembler {

    public AuthUserResponse toUserResponse(AiUserEntity user) {
        return AuthUserResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .department(user.getDepartment())
                .province(user.getProvince())
                .city(user.getCity())
                .employeeId(user.getEmployeeId())
                .roles(user.getRoles())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    public RoleOptionResponse toRoleResponse(AiRoleEntity role) {
        return RoleOptionResponse.builder()
                .id(role.getId())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .build();
    }

    public RoleTokenLimitResponse toRoleTokenLimitResponse(AiRoleTokenLimitEntity entity) {
        return RoleTokenLimitResponse.builder()
                .id(entity.getId())
                .roleId(entity.getRoleId())
                .roleName(entity.getRoleName())
                .roleDescription(entity.getRoleDescription())
                .botType(entity.getBotType())
                .dailyTokenLimit(entity.getDailyTokenLimit())
                .enabled(Boolean.TRUE.equals(entity.getEnabled()))
                .build();
    }

    public UserTokenLimitResponse toUserTokenLimitResponse(AiUserTokenLimitEntity entity) {
        return UserTokenLimitResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .username(entity.getUsername())
                .department(entity.getDepartment())
                .botType(entity.getBotType())
                .dailyTokenLimit(entity.getDailyTokenLimit())
                .enabled(Boolean.TRUE.equals(entity.getEnabled()))
                .build();
    }
}
