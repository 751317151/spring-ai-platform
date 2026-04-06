package com.huah.ai.platform.auth.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RoleTokenLimitResponse {
    Long id;
    Long roleId;
    String roleName;
    String roleDescription;
    String botType;
    int dailyTokenLimit;
    boolean enabled;
}
