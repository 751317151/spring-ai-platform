package com.huah.ai.platform.auth.dto;

import lombok.Data;

@Data
public class LogoutRequest {
    private String refreshToken;
}
