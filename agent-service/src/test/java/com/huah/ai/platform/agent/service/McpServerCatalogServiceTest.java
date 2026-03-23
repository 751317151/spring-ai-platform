package com.huah.ai.platform.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huah.ai.platform.agent.dto.McpServerListResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.core.io.ByteArrayResource;

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

        McpServerCatalogService service = new McpServerCatalogService(new ObjectMapper());
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
        McpServerCatalogService service = new McpServerCatalogService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "clientEnabled", false);
        ReflectionTestUtils.setField(service, "mcpServersResource",
                new ByteArrayResource("{\"other\":{}}".getBytes(StandardCharsets.UTF_8)));

        McpServerListResponse response = service.listServers();

        assertFalse(response.isClientEnabled());
        assertEquals(0, response.getCount());
        assertTrue(response.getServers().isEmpty());
    }
}
