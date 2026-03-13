package com.huah.ai.platform.auth.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * AI 平台用户实体
 */
@Data
@Entity
@Table(name = "ai_users", indexes = {
    @Index(name = "idx_users_username", columnList = "username")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUser {
    @Id
    private String id;

    @Column(unique = true, nullable = false)
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
