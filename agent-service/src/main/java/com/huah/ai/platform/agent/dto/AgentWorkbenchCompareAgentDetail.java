package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentWorkbenchCompareAgentDetail {
    private String agentType;
    private String summary;
    private String healthSummary;
    private String policySummary;
    private long totalCalls;
    private String failureRateLabel;
    private String riskLevel;
    private List<String> highlights;
    private List<String> topErrorTypes;
    private List<AgentWorkbenchChangeItem> recentChanges;
    private List<AgentWorkbenchFailureItem> recentFailures;
}
