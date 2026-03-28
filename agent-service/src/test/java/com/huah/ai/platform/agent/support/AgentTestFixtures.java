package com.huah.ai.platform.agent.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huah.ai.platform.agent.config.ToolsProperties;
import com.huah.ai.platform.agent.security.ToolSecurityService;

public final class AgentTestFixtures {

    private AgentTestFixtures() {
    }

    public static ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    public static ToolsProperties toolsProperties() {
        return new ToolsProperties();
    }

    public static ToolSecurityService toolSecurityService(ToolsProperties properties) {
        return new ToolSecurityService(properties);
    }
}
