package com.huah.ai.platform.auth.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserTokenLimitResponse {
    Long id;
    String userId;
    String username;
    String department;
    String botType;
    int dailyTokenLimit;
    boolean enabled;
}
