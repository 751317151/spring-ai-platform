package com.huah.ai.platform.agent.multi;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_multi_agent_traces")
public class MultiAgentExecutionTrace {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String traceId;
    private String userId;
    private String sessionId;
    private String agentType;
    private String requestSummary;
    private String finalSummary;
    private String status;
    private Integer totalPromptTokens;
    private Integer totalCompletionTokens;
    private Long totalLatencyMs;
    private Integer stepCount;
    private String errorMessage;
    private String parentTraceId;
    private String recoverySourceTraceId;
    private Integer recoverySourceStepOrder;
    private String recoveryAction;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
