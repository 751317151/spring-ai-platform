package com.huah.ai.platform.agent.config;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class McpChatClientConfiguration {

    private final AgentChatClientFactory chatClientFactory;

    @Bean
    @ConditionalOnProperty(name = "spring.ai.mcp.client.enabled", havingValue = "true")
    public ChatClient mcpChatClient(ChatModel model,
                                    ChatMemory chatMemory,
                                    ToolCallbackProvider toolCallbackProvider) {
        return chatClientFactory.buildMcpChatClient(
                model,
                chatMemory,
                toolCallbackProvider,
                AgentSystemPrompts.MCP
        );
    }
}
