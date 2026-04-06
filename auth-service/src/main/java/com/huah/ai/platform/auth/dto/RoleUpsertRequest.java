package com.huah.ai.platform.auth.dto;

import lombok.Data;

@Data
public class RoleUpsertRequest {
    private String roleName;
    private String description;
}
