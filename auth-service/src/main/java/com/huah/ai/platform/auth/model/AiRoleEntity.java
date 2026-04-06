package com.huah.ai.platform.auth.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 平台角色实体
 */
@Data
@TableName("ai_roles")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRoleEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String roleName; // ROLE_ADMIN, ROLE_RD, ROLE_SALES, ROLE_HR...

    private String description;
}
