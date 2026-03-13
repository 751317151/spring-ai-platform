package com.huah.ai.platform.auth.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * AI 平台角色实体
 */
@Data
@Entity
@Table(name = "ai_roles")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRole {
    @Id
    private String id;

    @Column(unique = true)
    private String roleName; // ROLE_ADMIN, ROLE_RD, ROLE_SALES, ROLE_HR...

    private String description;
}
