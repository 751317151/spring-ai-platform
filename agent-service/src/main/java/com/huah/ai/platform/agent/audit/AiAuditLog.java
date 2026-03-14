package com.huah.ai.platform.agent.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * AI 调用审计日志 - 在 agent-service 内定义，AOP 切点在同一 JVM 内有效
 */
@Data
@TableName("ai_audit_logs")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAuditLog {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String userId;
    private String agentType;
    private String modelId;

    private String userMessage;
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
