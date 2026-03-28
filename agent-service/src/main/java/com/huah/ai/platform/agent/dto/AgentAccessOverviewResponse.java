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
public class AgentAccessOverviewResponse {
    private String agentType;
    private boolean securityEnabled;
    private AgentRuntimePolicySummary runtimePolicySummary;
    private List<AgentAccessRuleItem> tools;
    private List<AgentAccessRuleItem> connectors;
    private List<AgentAccessRuleItem> mcpServers;
    private String summary;
}
