package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiAgentTraceResponse {
    private String traceId;
    private String sessionId;
    private String userId;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<MultiAgentTraceStepResponse> steps;
}
