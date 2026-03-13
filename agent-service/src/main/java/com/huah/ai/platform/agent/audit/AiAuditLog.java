package com.huah.ai.platform.agent.audit;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * AI 调用审计日志 - 在 agent-service 内定义，AOP 切点在同一 JVM 内有效
 */
@Data
@Entity
@Table(name = "ai_audit_logs",
    indexes = {
        @Index(name = "idx_audit_user", columnList = "userId"),
        @Index(name = "idx_audit_created", columnList = "createdAt")
    })
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAuditLog {

    @Id
    private String id;
    private String userId;
    private String agentType;
    private String modelId;

    @Column(columnDefinition = "TEXT")
    private String userMessage;

    @Column(columnDefinition = "TEXT")
    private String aiResponse;

    private Integer promptTokens;
    private Integer completionTokens;
    private Long latencyMs;
    private Boolean success;
    private String errorMessage;
    private String clientIp;
    private String sessionId;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

@Repository
interface AiAuditLogRepository extends JpaRepository<AiAuditLog, String> {}
