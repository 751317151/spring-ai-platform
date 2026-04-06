package com.huah.ai.platform.auth.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BotPermissionResponse {
    Long id;
    String botType;
    String allowedRoles;
    String allowedDepartments;
    String dataScope;
    String allowedOperations;
    int dailyTokenLimit;
    boolean enabled;
}
