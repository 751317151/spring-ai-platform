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
public class AgentWorkbenchCompareResponse {
    private AgentWorkbenchSummaryResponse left;
    private AgentWorkbenchSummaryResponse right;
    private String summary;
    private java.util.List<AgentWorkbenchCompareMetric> metrics;
    private List<AgentWorkbenchCompareInsight> insights;
    private AgentWorkbenchCompareAgentDetail leftDetail;
    private AgentWorkbenchCompareAgentDetail rightDetail;
    private List<AgentWorkbenchCompareChangeItem> changeComparison;
}
