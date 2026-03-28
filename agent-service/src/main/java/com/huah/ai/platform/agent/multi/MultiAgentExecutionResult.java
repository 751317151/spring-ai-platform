package com.huah.ai.platform.agent.multi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiAgentExecutionResult {
    private String traceId;
    private String sessionId;
    private String userId;
    private String content;
    private int promptTokens;
    private int completionTokens;
    private long latencyMs;
    private boolean success;
    private String errorMessage;
    private List<MultiAgentExecutionStep> steps;
}
