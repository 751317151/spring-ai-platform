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
public class AgentDiagnosticsResponse {
    private String agentType;
    private boolean accessible;
    private boolean toolSecurityEnabled;
    private List<String> allowedTools;
    private List<String> allowedConnectors;
    private List<String> allowedMcpServers;
    private List<String> enabledConnectors;
    private int recentMultiTraceCount;
    private int availableMcpServerCount;
    private int mcpIssueCount;
    private String summary;
}
