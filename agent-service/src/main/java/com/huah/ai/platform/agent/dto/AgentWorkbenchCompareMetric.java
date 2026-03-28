package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentWorkbenchCompareMetric {
    private String key;
    private String label;
    private String leftValue;
    private String rightValue;
    private String delta;
    private String trend;
    private String winnerAgentType;
    private String summary;
}
