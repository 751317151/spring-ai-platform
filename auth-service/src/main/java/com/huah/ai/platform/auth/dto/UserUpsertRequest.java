package com.huah.ai.platform.auth.dto;

import lombok.Data;

@Data
public class UserUpsertRequest {
    private String userId;
    private String username;
    private String password;
    private String department;
    private String province;
    private String city;
    private String employeeId;
    private String roles;
    private String enabled;
}
