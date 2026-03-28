package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.dto.McpServerListResponse;
import com.huah.ai.platform.agent.config.ToolsProperties;
import com.huah.ai.platform.agent.support.AgentTestFixtures;
import com.huah.ai.platform.agent.security.ToolSecurityService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerCatalogServiceTest {

    @Test
    void shouldReadServersFromClasspathStyleJson() {
        String json = """
                {
                  "mcpServers": {
                    "sequential-thinking": {
                      "command": "npx.cmd",
                      "args": ["-y", "@modelcontextprotocol/server-sequential-thinking"]
                    },
                    "disabled-demo": {
                      "command": "node",
                      "args": ["demo.js"],
                      "enabled": false
                    }
                  }
                }
                """;

        ToolsProperties properties = AgentTestFixtures.toolsProperties();
        McpServerCatalogService service = new McpServerCatalogService(
                AgentTestFixtures.objectMapper(),
                AgentTestFixtures.toolSecurityService(properties));
        ReflectionTestUtils.setField(service, "clientEnabled", true);
        ReflectionTestUtils.setField(service, "mcpServersResource",
                new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)));

        McpServerListResponse response = service.listServers();

        assertTrue(response.isClientEnabled());
        assertEquals(2, response.getCount());
        assertEquals("disabled-demo", response.getServers().get(0).getCode());
        assertFalse(response.getServers().get(0).isEnabled());
        assertEquals("npx.cmd", response.getServers().get(1).getCommand());
        assertEquals(2, response.getServers().get(1).getArgs().size());
    }

    @Test
    void shouldReturnEmptyWhenMcpServersNodeMissing() {
        ToolsProperties properties = AgentTestFixtures.toolsProperties();
        McpServerCatalogService service = new McpServerCatalogService(
                AgentTestFixtures.objectMapper(),
                AgentTestFixtures.toolSecurityService(properties));
        ReflectionTestUtils.setField(service, "clientEnabled", false);
        ReflectionTestUtils.setField(service, "mcpServersResource",
                new ByteArrayResource("{\"other\":{}}".getBytes(StandardCharsets.UTF_8)));

        McpServerListResponse response = service.listServers();

        assertFalse(response.isClientEnabled());
        assertEquals(0, response.getCount());
        assertTrue(response.getServers().isEmpty());
    }

    @Test
    void shouldExposeAuthorizationDecisionForAgentView() {
        String json = """
                {
                  "mcpServers": {
                    "knowledge-mcp": {
                      "command": "node",
                      "args": ["knowledge.js"]
                    }
                  }
                }
                """;

        ToolsProperties properties = AgentTestFixtures.toolsProperties();
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().getAgentMcpServerAllowlist().put("rd", java.util.List.of("ops-mcp"));

        McpServerCatalogService service = new McpServerCatalogService(
                AgentTestFixtures.objectMapper(),
                AgentTestFixtures.toolSecurityService(properties));
        ReflectionTestUtils.setField(service, "clientEnabled", true);
        ReflectionTestUtils.setField(service, "mcpServersResource",
                new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)));

        McpServerListResponse response = service.listServers("rd");

        assertEquals(0, response.getCount());
        var item = service.listAllServers("rd").get(0);
        assertFalse(item.isAuthorized());
        assertEquals("MCP_DENIED", item.getAccessReasonCode());
        assertTrue(item.getAccessReasonMessage().contains("not allowed"));
    }
}
