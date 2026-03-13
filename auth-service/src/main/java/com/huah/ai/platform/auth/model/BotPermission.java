package com.huah.ai.platform.auth.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.Set;

/**
 * Bot 权限配置实体
 * 控制每个 AI Bot 类型的访问角色、部门范围、Token 限额
 */
@Data
@Entity
@Table(name = "ai_bot_permissions")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotPermission {
    @Id
    private String id;

    /** Bot 类型: rd | sales | hr | finance | supply-chain | qc */
    @Column(nullable = false, unique = true)
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

    public boolean hasRole(String role) {
        if (allowedRoles == null || allowedRoles.isBlank()) return true;
        return Set.of(allowedRoles.split(",")).contains(role.trim());
    }

    public boolean hasDepartment(String dept) {
        if (allowedDepartments == null || allowedDepartments.isBlank()) return true;
        return Set.of(allowedDepartments.split(",")).contains(dept.trim());
    }
}
