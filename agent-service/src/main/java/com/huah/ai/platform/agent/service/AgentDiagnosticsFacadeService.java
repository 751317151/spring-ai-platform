package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.controller.AgentApiConstants;
import com.huah.ai.platform.agent.dto.AgentDiagnosticsResponse;
import com.huah.ai.platform.agent.dto.McpServerListResponse;
import com.huah.ai.platform.agent.dto.ToolSecurityOverviewResponse;
import com.huah.ai.platform.agent.multi.MultiAgentTraceService;
import com.huah.ai.platform.agent.security.ToolSecurityService;
import com.huah.ai.platform.agent.tools.InternalApiTools;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentDiagnosticsFacadeService {

    private final MultiAgentTraceService multiAgentTraceService;
    private final ToolSecurityService toolSecurityService;
    private final McpServerCatalogService mcpServerCatalogService;
    private final InternalApiTools internalApiTools;

    public ToolSecurityOverviewResponse buildToolSecurityOverview(String agentType) {
        return ToolSecurityOverviewResponse.builder()
                .securityEnabled(toolSecurityService.isSecurityEnabled())
                .agentType(agentType)
                .allowedTools(toolSecurityService.getAllowedTools(agentType))
                .allowedConnectors(toolSecurityService.getAllowedConnectors(agentType))
                .enabledConnectors(internalApiTools.listEnabledConnectorCodes())
                .build();
    }

    public AgentDiagnosticsResponse buildDiagnostics(String agentType, String userId) {
        McpServerListResponse mcpServers = mcpServerCatalogService.listServers(agentType);
        List<String> allowedTools = toolSecurityService.getAllowedTools(agentType);
        List<String> allowedConnectors = toolSecurityService.getAllowedConnectors(agentType);
        List<String> allowedMcpServers = toolSecurityService.getAllowedMcpServers(agentType);
        int recentMultiTraceCount = AgentApiConstants.AGENT_TYPE_MULTI.equals(agentType)
                ? multiAgentTraceService.listTraces(userId, null, 10).size()
                : 0;
        return AgentDiagnosticsResponse.builder()
                .agentType(agentType)
                .accessible(true)
                .toolSecurityEnabled(toolSecurityService.isSecurityEnabled())
                .allowedTools(allowedTools)
                .allowedConnectors(allowedConnectors)
                .allowedMcpServers(allowedMcpServers)
                .enabledConnectors(internalApiTools.listEnabledConnectorCodes())
                .recentMultiTraceCount(recentMultiTraceCount)
                .availableMcpServerCount(mcpServers.getCount())
                .mcpIssueCount(mcpServers.getIssueCount())
                .summary(buildSummary(
                        agentType,
                        allowedTools,
                        allowedConnectors,
                        allowedMcpServers,
                        mcpServers.getCount(),
                        mcpServers.getIssueCount(),
                        recentMultiTraceCount))
                .build();
    }

    private String buildSummary(
            String agentType,
            List<String> allowedTools,
            List<String> allowedConnectors,
            List<String> allowedMcpServers,
            int mcpServerCount,
            int mcpIssueCount,
            int recentMultiTraceCount) {
        List<String> fragments = new ArrayList<>();
        fragments.add("agent=" + agentType);
        fragments.add("tools=" + allowedTools.size());
        fragments.add("connectors=" + allowedConnectors.size());
        fragments.add("mcpServers=" + mcpServerCount);
        if (!allowedMcpServers.isEmpty()) {
            fragments.add("mcpAllow=" + allowedMcpServers.size());
        }
        if (mcpIssueCount > 0) {
            fragments.add("mcpIssues=" + mcpIssueCount);
        }
        if (recentMultiTraceCount > 0) {
            fragments.add("recentMultiTraces=" + recentMultiTraceCount);
        }
        return String.join(", ", fragments);
    }
}
