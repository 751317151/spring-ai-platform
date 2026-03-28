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
public class ToolSecurityOverviewResponse {
    private boolean securityEnabled;
    private String agentType;
    private List<String> allowedTools;
    private List<String> allowedConnectors;
    private List<String> enabledConnectors;
}
