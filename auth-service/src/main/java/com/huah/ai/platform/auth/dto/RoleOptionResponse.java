package com.huah.ai.platform.auth.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RoleOptionResponse {
    Long id;
    String roleName;
    String description;
}
