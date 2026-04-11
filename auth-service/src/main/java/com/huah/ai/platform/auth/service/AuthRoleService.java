package com.huah.ai.platform.auth.service;

import com.huah.ai.platform.auth.mapper.AiRoleMapper;
import com.huah.ai.platform.auth.mapper.AiUserRoleMapper;
import com.huah.ai.platform.auth.model.AiRoleEntity;
import com.huah.ai.platform.auth.model.AiUserRoleEntity;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthRoleService {

    private static final String DEFAULT_ROLE = "ROLE_USER";

    private final AiRoleMapper roleMapper;
    private final AiUserRoleMapper userRoleMapper;

    public void validateRolesOrThrow(String roles) {
        resolveRoles(roles, true);
    }

    public void replaceUserRoles(String userId, String roles) {
        List<AiRoleEntity> roleEntities = resolveRoles(roles, true);
        userRoleMapper.deleteByUserId(userId);
        for (AiRoleEntity roleEntity : roleEntities) {
            userRoleMapper.insert(AiUserRoleEntity.builder()
                    .userId(userId)
                    .roleId(roleEntity.getId())
                    .build());
        }
    }

    private List<String> parseRoles(String roles, boolean applyDefaultRole) {
        Set<String> normalized = new LinkedHashSet<>();
        if (roles != null) {
            for (String role : roles.split(",")) {
                String trimmedRole = role.trim();
                if (!trimmedRole.isEmpty()) {
                    normalized.add(trimmedRole.toUpperCase(Locale.ROOT));
                }
            }
        }
        if (applyDefaultRole && normalized.isEmpty()) {
            normalized.add(DEFAULT_ROLE);
        }
        return List.copyOf(normalized);
    }

    private List<AiRoleEntity> resolveRoles(String roles, boolean applyDefaultRole) {
        List<String> normalizedRoles = parseRoles(roles, applyDefaultRole);
        if (normalizedRoles.isEmpty()) {
            return List.of();
        }
        List<AiRoleEntity> roleEntities = roleMapper.selectByRoleNames(normalizedRoles);
        if (roleEntities.size() != normalizedRoles.size()) {
            Set<String> existingRoleNames = roleEntities.stream()
                    .map(AiRoleEntity::getRoleName)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            List<String> missingRoles = normalizedRoles.stream()
                    .filter(roleName -> !existingRoleNames.contains(roleName))
                    .toList();
            throw new IllegalArgumentException("\u89d2\u8272\u4e0d\u5b58\u5728: " + String.join(",", missingRoles));
        }
        return roleEntities;
    }
}
