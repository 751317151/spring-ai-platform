package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 销售报价助手
 */
@Service
@RequiredArgsConstructor
public class SalesAssistantAgent {
    private final ChatClient.Builder chatClientBuilder;
    private final ConversationMemoryService memoryService;

    private static final String SYSTEM = """
            你是企业销售报价智能助手，帮助销售人员快速：
            1. 查询产品报价和折扣政策
            2. 分析客户需求，推荐合适产品
            3. 生成报价单草稿
            4. 查询历史订单和客户信息
            5. 提供竞品对比分析
            当前用户: {userId}
            """;

    public String chat(String userId, String sessionId, String message) {
        return chatClientBuilder
                .defaultSystem(SYSTEM)
                .defaultAdvisors(new SimpleLoggerAdvisor(), MessageChatMemoryAdvisor.builder(memoryService.getOrCreateMemory(sessionId)).build())
                .build()
                .prompt()
                .system(s -> s.param("userId", userId))
                .user(message)
                .call().content();
    }

    public Flux<String> chatStream(String userId, String sessionId, String message) {
        return chatClientBuilder
                .defaultSystem(SYSTEM)
                .defaultAdvisors(new SimpleLoggerAdvisor(), MessageChatMemoryAdvisor.builder(memoryService.getOrCreateMemory(sessionId)).build())
                .build()
                .prompt()
                .system(s -> s.param("userId", userId))
                .user(message)
                .stream().content();
    }
}
