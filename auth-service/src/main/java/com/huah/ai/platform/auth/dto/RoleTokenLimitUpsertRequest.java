package com.huah.ai.platform.auth.dto;

import lombok.Data;

@Data
public class RoleTokenLimitUpsertRequest {
    private Long roleId;
    private String botType;
    private Integer dailyTokenLimit;
    private Boolean enabled;
}
