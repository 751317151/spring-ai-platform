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
import java.util.Set;

/**
 * Bot 权限配置实体
 * 控制每个 AI Bot 类型的访问角色、部门范围、Token 限额
 */
@Data
@TableName("ai_bot_permissions")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotPermissionEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** Bot 类型: rd | sales | hr | finance | supply-chain | qc */
    private String botType;

    /** 允许访问的角色（逗号分隔）: ROLE_RD,ROLE_ADMIN */
    private String allowedRoles;

    /** 允许访问的部门（逗号分隔，空=全部） */
    private String allowedDepartments;

    /** 数据范围: ALL | DEPARTMENT | SELF */
    @Builder.Default
    private String dataScope = "DEPARTMENT";

    /** 允许的操作: READ | WRITE | APPROVE */
    private String allowedOperations;

    /** 单用户每日最大 Token 消耗 */
    @Builder.Default
    private int dailyTokenLimit = 100000;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public boolean hasRole(String role) {
        if (allowedRoles == null || allowedRoles.isBlank()) return true;
        return Set.of(allowedRoles.split(",")).contains(role.trim());
    }

    public boolean hasDepartment(String dept) {
        if (allowedDepartments == null || allowedDepartments.isBlank()) return true;
        return Set.of(allowedDepartments.split(",")).contains(dept.trim());
    }
}
