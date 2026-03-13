package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HrAssistantAgent {
    private final ChatClient.Builder chatClientBuilder;
    private final ConversationMemoryService memoryService;

    public String chat(String userId, String sessionId, String message) {
        return chatClientBuilder
                .defaultSystem("你是HR行政智能助手，帮助员工处理：假期查询、请假申请、薪资查询、公司政策咨询、入离职手续。用户:{userId}")
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memoryService.getOrCreateMemory(sessionId)).build())
                .build()
                .prompt().system(s -> s.param("userId", userId)).user(message).call().content();
    }
}

