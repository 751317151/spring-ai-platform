package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentWorkbenchTrendPoint {
    private String label;
    private long totalCalls;
    private long failureCalls;
    private long toolCalls;
    private long avgLatencyMs;
}
