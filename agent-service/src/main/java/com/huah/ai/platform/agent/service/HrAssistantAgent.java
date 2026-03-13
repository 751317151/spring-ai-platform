package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class HrAssistantAgent {
    private final ChatClient.Builder chatClientBuilder;
    private final ConversationMemoryService memoryService;

    private static final String SYSTEM = "你是HR行政智能助手，帮助员工处理：假期查询、请假申请、薪资查询、公司政策咨询、入离职手续。用户:{userId}";

    public String chat(String userId, String sessionId, String message) {
        return chatClientBuilder
                .defaultSystem(SYSTEM)
                .defaultAdvisors(new SimpleLoggerAdvisor(), MessageChatMemoryAdvisor.builder(memoryService.getOrCreateMemory(sessionId)).build())
                .build()
                .prompt().system(s -> s.param("userId", userId)).user(message).call().content();
    }

    public Flux<String> chatStream(String userId, String sessionId, String message) {
        return chatClientBuilder
                .defaultSystem(SYSTEM)
                .defaultAdvisors(new SimpleLoggerAdvisor(), MessageChatMemoryAdvisor.builder(memoryService.getOrCreateMemory(sessionId)).build())
                .build()
                .prompt().system(s -> s.param("userId", userId)).user(message).stream().content();
    }
}

