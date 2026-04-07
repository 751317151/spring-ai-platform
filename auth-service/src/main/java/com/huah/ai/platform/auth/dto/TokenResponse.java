package com.huah.ai.platform.auth.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TokenResponse {
    String token;
    String refreshToken;
    String tokenType;
    long expiresIn;
    long refreshExpiresIn;
    String userId;
    String username;
    String roles;
    String department;
    String province;
    String city;
}
