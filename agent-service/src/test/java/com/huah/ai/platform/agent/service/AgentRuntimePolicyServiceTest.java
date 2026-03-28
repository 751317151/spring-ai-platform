package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.config.ToolsProperties;
import com.huah.ai.platform.agent.dto.AgentRuntimePolicySummary;
import com.huah.ai.platform.agent.dto.McpServerInfo;
import com.huah.ai.platform.agent.security.ToolSecurityService;
import com.huah.ai.platform.agent.security.AgentAccessChecker;
import com.huah.ai.platform.agent.support.AgentTestFixtures;
import com.huah.ai.platform.agent.tools.InternalApiTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRuntimePolicyServiceTest {

    private AgentRuntimePolicyService agentRuntimePolicyService;

    @BeforeEach
    void setUp() {
        ToolsProperties properties = AgentTestFixtures.toolsProperties();
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().getAgentToolAllowlist().put("rd", List.of("*"));
        properties.getSecurity().getAgentConnectorAllowlist().put("rd", List.of("knowledge"));
        properties.getSecurity().getAgentConnectorResourceAllowlist().put("rd", java.util.Map.of(
                "knowledge", List.of("/dept-a/")
        ));
        properties.getSecurity().getAgentMcpServerAllowlist().put("rd", List.of("knowledge-mcp"));
        properties.getSecurity().getAgentMcpToolAllowlist().put("rd", java.util.Map.of(
                "knowledge-mcp", List.of("searchDocuments")
        ));
        properties.getSecurity().getAgentDataScopeAllowlist().put("rd", List.of("analytics.orders"));
        properties.getSecurity().getAgentDataSourceAllowlist().put("rd", List.of("warehouse"));
        properties.getSecurity().getAgentCrossSchemaAccessAllowlist().put("rd", List.of("analytics:public"));
        properties.getSecurity().getSchemaDataSourceBindings().put("analytics", "warehouse");
        properties.getSecurity().getSchemaDataSourceBindings().put("public", "warehouse");
        properties.getSecurity().getAgentMaxConcurrency().put("rd", 4);
        properties.getSecurity().getAgentMaxQueueDepth().put("rd", 2);
        properties.getSecurity().getAgentQueueWaitTimeoutMs().put("rd", 1500L);
        properties.getSecurity().getAgentRequestTimeoutMs().put("rd", 45000L);
        properties.getSecurity().getAgentStreamTimeoutMs().put("rd", 120000L);

        ToolsProperties.ConnectorDefinition connectorDefinition = new ToolsProperties.ConnectorDefinition();
        connectorDefinition.setEnabled(true);
        connectorDefinition.setAllowedPathPrefixes(List.of("/fallback/"));
        properties.getInternalApi().getConnectors().put("knowledge", connectorDefinition);

        ToolSecurityService toolSecurityService = AgentTestFixtures.toolSecurityService(properties);
        InternalApiTools internalApiTools = mock(InternalApiTools.class);
        when(internalApiTools.listEnabledConnectorCodes()).thenReturn(List.of("knowledge"));

        McpServerCatalogService mcpServerCatalogService = mock(McpServerCatalogService.class);
        when(mcpServerCatalogService.listAllServers("rd")).thenReturn(List.of(
                McpServerInfo.builder().code("knowledge-mcp").build()
        ));
        AgentAccessChecker agentAccessChecker = mock(AgentAccessChecker.class);
        when(agentAccessChecker.getDailyTokenLimit("rd")).thenReturn(20000);
        AgentRuntimeIsolationService isolationService = new AgentRuntimeIsolationService(properties);

        agentRuntimePolicyService = new AgentRuntimePolicyService(
                toolSecurityService,
                properties,
                internalApiTools,
                mcpServerCatalogService,
                agentAccessChecker,
                isolationService
        );
    }

    @Test
    void shouldBuildRuntimePolicySummary() {
        AgentRuntimePolicySummary summary = agentRuntimePolicyService.build("rd");

        assertTrue(summary.isSecurityEnabled());
        assertTrue(summary.isConnectorResourceIsolationEnabled());
        assertTrue(summary.isMcpToolIsolationEnabled());
        assertTrue(summary.isDataScopeIsolationEnabled());
        assertTrue(summary.isDataSourceIsolationEnabled());
        assertTrue(summary.isCrossSchemaAccessControlled());
        assertTrue(summary.isConcurrencyIsolationEnabled());
        assertTrue(summary.isQueueGovernanceEnabled());
        assertTrue(summary.isWildcardToolAccess());
        assertEquals("low", summary.getRiskLevel());
        assertEquals(6, summary.getRestrictedResourceCount());
        assertTrue(summary.getSummary().contains("dataSourceIsolation=on"));
        assertTrue(summary.getSummary().contains("crossSchemaControl=on"));
        assertTrue(summary.getSummary().contains("maxConcurrency=4"));
        assertTrue(summary.getSummary().contains("maxQueueDepth=2"));
        assertEquals(8, summary.getHighlights().size());
    }
}
