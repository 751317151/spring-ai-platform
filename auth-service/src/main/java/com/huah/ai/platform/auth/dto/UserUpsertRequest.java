package com.huah.ai.platform.auth.dto;

import lombok.Data;

@Data
public class UserUpsertRequest {
    private String username;
    private String password;
    private String department;
    private String employeeId;
    private String roles;
    private String enabled;
}
