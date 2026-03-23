package com.huah.ai.platform.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "spring.ai.mcp.client.enabled", havingValue = "true")
public class McpAssistantAgent extends BaseAssistantAgent {

    public McpAssistantAgent(@Qualifier("mcpChatClient") ChatClient chatClient) {
        super("mcp", chatClient);
    }
}
