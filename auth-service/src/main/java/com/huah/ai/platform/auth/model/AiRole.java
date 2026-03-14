package com.huah.ai.platform.auth.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * AI 平台角色实体
 */
@Data
@TableName("ai_roles")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRole {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String roleName; // ROLE_ADMIN, ROLE_RD, ROLE_SALES, ROLE_HR...

    private String description;
}
