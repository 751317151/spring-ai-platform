package com.huah.ai.platform.agent.audit;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 璋冪敤瀹¤鏃ュ織 - 鍦?agent-service 鍐呭畾涔夛紝AOP 鍒囩偣鍦ㄥ悓涓€ JVM 鍐呮湁鏁? */
@Data
@TableName("ai_audit_logs")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAuditLogEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
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
    private String country;
    private String province;
    private String city;
    private String sessionId;
    private String traceId;
    private String phaseBreakdownJson;

    @Builder.Default
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt = LocalDateTime.now();
}

