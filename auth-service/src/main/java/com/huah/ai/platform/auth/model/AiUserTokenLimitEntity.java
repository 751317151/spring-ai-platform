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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_user_token_limits")
public class AiUserTokenLimitEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String userId;

    private String botType;

    private Integer dailyTokenLimit;

    @Builder.Default
    private Boolean enabled = true;

    @Builder.Default
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @TableField(exist = false)
    private String username;

    @TableField(exist = false)
    private String department;
}
