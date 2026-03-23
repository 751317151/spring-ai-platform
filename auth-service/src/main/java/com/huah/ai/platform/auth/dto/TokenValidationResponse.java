package com.huah.ai.platform.auth.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TokenValidationResponse {
    String userId;
    String roles;
    String department;
}
