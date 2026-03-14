package com.huah.ai.platform.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * 生产质控助手 Agent
 * 功能：不良品分析、质检报告查询、质量趋势预警
 */
@Service
public class QcAssistantAgent {

    private final ChatClient chatClient;

    public QcAssistantAgent(@Qualifier("qcChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String chat(String userId, String sessionId, String message) {
        return chatClient
                .prompt()
                .system(s -> s.param("userId", userId))
                .user(message)
                .advisors(a -> a.param(CONVERSATION_ID, sessionId))
                .call()
                .content();
    }

    public Flux<String> chatStream(String userId, String sessionId, String message) {
        return chatClient
                .prompt()
                .system(s -> s.param("userId", userId))
                .user(message)
                .advisors(a -> a.param(CONVERSATION_ID, sessionId))
                .stream()
                .content();
    }
}
