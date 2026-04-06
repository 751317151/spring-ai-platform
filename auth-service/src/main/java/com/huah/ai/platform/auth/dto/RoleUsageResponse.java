package com.huah.ai.platform.auth.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RoleUsageResponse {
    Long roleId;
    String roleName;
    int userCount;
    int permissionCount;
    List<String> userReferences;
    List<String> permissionReferences;
}
