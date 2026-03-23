package com.huah.ai.platform.agent.tools;

import com.huah.ai.platform.agent.config.ToolsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

class InternalApiToolsTest {

    private InternalApiTools internalApiTools;

    @BeforeEach
    void setUp() {
        ToolsProperties properties = new ToolsProperties();

        ToolsProperties.ConnectorDefinition enabled = new ToolsProperties.ConnectorDefinition();
        enabled.setEnabled(true);
        enabled.setName("Issue Center");
        enabled.setBaseUrl("http://localhost:8090");
        enabled.setAllowedPathPrefixes(List.of("/api/issues"));

        ToolsProperties.ConnectorDefinition disabled = new ToolsProperties.ConnectorDefinition();
        disabled.setEnabled(false);
        disabled.setName("Disabled");
        disabled.setBaseUrl("http://localhost:8091");

        properties.getInternalApi().getConnectors().put("issue-center", enabled);
        properties.getInternalApi().getConnectors().put("disabled-api", disabled);

        internalApiTools = new InternalApiTools(mock(RestClient.Builder.class, RETURNS_DEEP_STUBS), properties);
    }

    @Test
    void shouldListEnabledConnectorsOnly() {
        List<Map<String, Object>> connectors = internalApiTools.listConnectors();
        assertEquals(1, connectors.size());
        assertEquals("issue-center", connectors.get(0).get("code"));
    }

    @Test
    void shouldRejectDisabledConnector() {
        Map<String, Object> result = internalApiTools.callConnector("disabled-api", "/api/issues", "{}");
        assertTrue(String.valueOf(result.get("error")).contains("未启用"));
    }

    @Test
    void shouldRejectPathOutsideWhitelist() {
        Map<String, Object> result = internalApiTools.callConnector("issue-center", "/api/users", "{}");
        assertTrue(String.valueOf(result.get("error")).contains("不在 connector 允许范围内"));
    }
}
