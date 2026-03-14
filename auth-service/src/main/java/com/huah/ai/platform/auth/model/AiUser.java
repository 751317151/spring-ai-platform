package com.huah.ai.platform.auth.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.time.LocalDateTime;

/**
 * AI 平台用户实体
 */
@Data
@TableName("ai_users")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUser {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String username;
    private String passwordHash;
    private String department;
    private String employeeId;

    /** Comma-separated roles: ROLE_ADMIN,ROLE_RD,ROLE_USER */
    private String roles;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime lastLoginAt;
}
