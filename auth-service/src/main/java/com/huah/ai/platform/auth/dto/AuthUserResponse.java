package com.huah.ai.platform.auth.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class AuthUserResponse {
    String userId;
    String username;
    String department;
    String province;
    String city;
    String employeeId;
    String roles;
    boolean enabled;
    LocalDateTime createdAt;
    LocalDateTime lastLoginAt;
}
