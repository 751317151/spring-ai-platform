package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiAgentTraceStepResponse {
    private Integer stepOrder;
    private String stage;
    private String agentName;
    private String inputSummary;
    private String outputSummary;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long latencyMs;
    private Boolean success;
    private String errorMessage;
    private Boolean recoverable;
    private Boolean skipped;
    private String recoveryAction;
    private String sourceTraceId;
    private Integer sourceStepOrder;
    private LocalDateTime createdAt;
}
