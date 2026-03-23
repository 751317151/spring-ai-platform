package com.huah.ai.platform.auth.dto;

import lombok.Data;

@Data
public class BotPermissionUpsertRequest {
    private String botType;
    private String allowedRoles;
    private String allowedDepartments;
    private String dataScope;
    private String allowedOperations;
    private Integer dailyTokenLimit;
    private Boolean enabled;
}
