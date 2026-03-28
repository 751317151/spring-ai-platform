package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentWorkbenchHealthSummary {
    private boolean accessible;
    private boolean failureSpike;
    private boolean toolFailureSpike;
    private boolean warning;
    private String summary;
}
