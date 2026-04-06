package com.huah.ai.platform.auth.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 平台用户实体
 */
@Data
@TableName("ai_users")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUserEntity {
    @TableId(value = "userid", type = IdType.INPUT)
    private String userId;

    private String username;
    private String passwordHash;
    private String department;
    private String employeeId;

    @TableField(exist = false)
    private String roles;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt = LocalDateTime.now();

    private LocalDateTime lastLoginAt;
}
