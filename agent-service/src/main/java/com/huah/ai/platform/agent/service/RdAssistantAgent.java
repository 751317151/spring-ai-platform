package com.huah.ai.platform.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * 研发助手 Agent
 * 功能：代码审查、技术文档查询、缺陷分析、技术选型建议
 */
@Slf4j
@Service
public class RdAssistantAgent {

    private final ChatClient chatClient;

    public RdAssistantAgent(@Qualifier("rdChatClient") ChatClient chatClient) {
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
