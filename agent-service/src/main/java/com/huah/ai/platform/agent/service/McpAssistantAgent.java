package com.huah.ai.platform.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Service
@ConditionalOnProperty(name = "spring.ai.mcp.client.enabled", havingValue = "true")
public class McpAssistantAgent {

    private final ChatClient chatClient;

    public McpAssistantAgent(@Qualifier("mcpChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public AgentChatResult chat(String userId, String sessionId, String message) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .system(s -> s.param("userId", userId))
                .user(message)
                .advisors(a -> a.param(CONVERSATION_ID, sessionId))
                .call()
                .chatResponse();
        return AgentChatResult.fromChatResponse(chatResponse);
    }

    public Flux<ChatResponse> chatStream(String userId, String sessionId, String message) {
        return chatClient
                .prompt()
                .system(s -> s.param("userId", userId))
                .user(message)
                .advisors(a -> a.param(CONVERSATION_ID, sessionId))
                .stream()
                .chatResponse();
    }
}
