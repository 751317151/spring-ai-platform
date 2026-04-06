package com.huah.ai.platform.auth.service;

import com.huah.ai.platform.auth.dto.AuthUserResponse;
import com.huah.ai.platform.auth.dto.BotPermissionResponse;
import com.huah.ai.platform.auth.model.AiUserEntity;
import com.huah.ai.platform.auth.model.BotPermissionEntity;
import org.springframework.stereotype.Component;

@Component
public class AuthViewAssembler {

    public AuthUserResponse toUserResponse(AiUserEntity user) {
        return AuthUserResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .department(user.getDepartment())
                .employeeId(user.getEmployeeId())
                .roles(user.getRoles())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    public BotPermissionResponse toPermissionResponse(BotPermissionEntity permission) {
        return BotPermissionResponse.builder()
                .id(permission.getId())
                .botType(permission.getBotType())
                .allowedRoles(permission.getAllowedRoles())
                .allowedDepartments(permission.getAllowedDepartments())
                .dataScope(permission.getDataScope())
                .allowedOperations(permission.getAllowedOperations())
                .dailyTokenLimit(permission.getDailyTokenLimit())
                .enabled(permission.isEnabled())
                .build();
    }
}
