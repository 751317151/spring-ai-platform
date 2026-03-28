package com.huah.ai.platform.agent.security;

import com.huah.ai.platform.agent.config.ToolsProperties;
import com.huah.ai.platform.agent.support.AgentTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSecurityServiceTest {

    private ToolSecurityService toolSecurityService;

    @BeforeEach
    void setUp() {
        ToolsProperties properties = AgentTestFixtures.toolsProperties();
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().getAgentConnectorAllowlist().put("rd", List.of("knowledge"));
        properties.getSecurity().getAgentConnectorResourceAllowlist().put("rd", java.util.Map.of(
                "knowledge", List.of("/dept-a/", "/shared/")
        ));
        properties.getSecurity().getAgentMcpServerAllowlist().put("rd", List.of("knowledge-mcp"));
        properties.getSecurity().getAgentMcpToolAllowlist().put("rd", java.util.Map.of(
                "knowledge-mcp", List.of("searchDocuments", "fetchChunk")
        ));
        properties.getSecurity().getAgentDataScopeAllowlist().put("rd", List.of("analytics.orders", "public.*"));
        properties.getSecurity().getAgentDataSourceAllowlist().put("rd", List.of("warehouse"));
        properties.getSecurity().getAgentCrossSchemaAccessAllowlist().put("rd", List.of("analytics:public"));
        properties.getSecurity().getSchemaDataSourceBindings().put("analytics", "warehouse");
        properties.getSecurity().getSchemaDataSourceBindings().put("public", "warehouse");
        properties.getSecurity().getSchemaDataSourceBindings().put("private", "erp");
        toolSecurityService = AgentTestFixtures.toolSecurityService(properties);
    }

    @Test
    void shouldAllowConnectorPathWithinConfiguredPrefix() {
        ToolAccessDecision decision = toolSecurityService.decideConnectorPathAccess(
                "rd",
                "knowledge",
                "/dept-a/project/overview.md",
                List.of("/fallback/")
        );

        assertTrue(decision.isAllowed());
        assertEquals("CONNECTOR_RESOURCE_ALLOWED", decision.getReasonCode());
        assertEquals("connector:knowledge/dept-a/project/overview.md", decision.getResource());
    }

    @Test
    void shouldDenyConnectorPathOutsideConfiguredPrefix() {
        ToolAccessDecision decision = toolSecurityService.decideConnectorPathAccess(
                "rd",
                "knowledge",
                "/dept-b/private/roadmap.md",
                List.of("/dept-b/")
        );

        assertFalse(decision.isAllowed());
        assertEquals("CONNECTOR_RESOURCE_DENIED", decision.getReasonCode());
        assertEquals("connector:knowledge/dept-b/private/roadmap.md", decision.getResource());
    }

    @Test
    void shouldReturnConfiguredMcpToolsForAgent() {
        List<String> tools = toolSecurityService.getAllowedMcpTools("rd", "knowledge-mcp");

        assertEquals(List.of("searchDocuments", "fetchChunk"), tools);
    }

    @Test
    void shouldDenyTableOutsideAllowedDataScope() {
        ToolAccessDecision decision = toolSecurityService.decideDataScopeAccess(
                "rd",
                "analytics",
                List.of("orders", "users")
        );

        assertFalse(decision.isAllowed());
        assertEquals("DATA_SCOPE_DENIED", decision.getReasonCode());
        assertEquals("data:analytics.users", decision.getResource());
    }

    @Test
    void shouldAllowWildcardSchemaRule() {
        ToolAccessDecision decision = toolSecurityService.decideDataScopeAccess(
                "rd",
                "public",
                List.of("knowledge_bases", "document_meta")
        );

        assertTrue(decision.isAllowed());
        assertEquals("DATA_SCOPE_ALLOWED", decision.getReasonCode());
    }

    @Test
    void shouldDenySchemaBoundToUnexpectedDataSourceWhenNoTablesProvided() {
        ToolAccessDecision decision = toolSecurityService.decideDataScopeAccess(
                "rd",
                "private",
                List.of()
        );

        assertFalse(decision.isAllowed());
        assertEquals("DATA_SOURCE_DENIED", decision.getReasonCode());
        assertEquals("data:private.*", decision.getResource());
    }

    @Test
    void shouldAllowConfiguredCrossSchemaAccessWithinSameDataSource() {
        ToolAccessDecision decision = toolSecurityService.decideDataScopeAccess(
                "rd",
                List.of(
                        new ToolSecurityService.DataScopeTarget("analytics", "orders"),
                        new ToolSecurityService.DataScopeTarget("public", "documents")
                )
        );

        assertTrue(decision.isAllowed());
        assertEquals("DATA_SCOPE_ALLOWED", decision.getReasonCode());
        assertTrue(decision.getDetail().contains("dataSources=[warehouse]"));
    }

    @Test
    void shouldDenyCrossSchemaPairOutsideConfiguredBoundary() {
        ToolAccessDecision decision = toolSecurityService.decideDataScopeAccess(
                "rd",
                List.of(
                        new ToolSecurityService.DataScopeTarget("analytics", "orders"),
                        new ToolSecurityService.DataScopeTarget("private", "payroll")
                )
        );

        assertFalse(decision.isAllowed());
        assertEquals("DATA_CROSS_SCHEMA_DENIED", decision.getReasonCode());
        assertTrue(decision.getDetail().contains("analytics:private"));
    }

    @Test
    void shouldDenySchemaBoundToUnexpectedDataSource() {
        ToolAccessDecision decision = toolSecurityService.decideDataScopeAccess(
                "rd",
                "private",
                List.of("employees")
        );

        assertFalse(decision.isAllowed());
        assertEquals("DATA_SOURCE_DENIED", decision.getReasonCode());
        assertTrue(decision.getDetail().contains("dataSource=erp"));
    }
}
