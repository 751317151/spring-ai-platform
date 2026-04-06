package com.huah.ai.platform.auth.dto;

import lombok.Data;

@Data
public class UserTokenLimitUpsertRequest {
    private String userId;
    private String botType;
    private Integer dailyTokenLimit;
    private Boolean enabled;
}
