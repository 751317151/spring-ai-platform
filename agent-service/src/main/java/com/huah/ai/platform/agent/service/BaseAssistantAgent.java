package com.huah.ai.platform.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

public abstract class BaseAssistantAgent implements AssistantAgent {

    private final String agentType;
    private final ChatClient chatClient;

    protected BaseAssistantAgent(String agentType, ChatClient chatClient) {
        this.agentType = agentType;
        this.chatClient = chatClient;
    }

    @Override
    public String getAgentType() {
        return agentType;
    }

    @Override
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

    @Override
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
