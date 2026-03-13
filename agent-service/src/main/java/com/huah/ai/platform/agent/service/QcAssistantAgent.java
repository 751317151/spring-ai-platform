package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 生产质控助手 Agent
 * 功能：不良品分析、质检报告查询、质量趋势预警
 */
@Service
@RequiredArgsConstructor
public class QcAssistantAgent {

    private final ChatClient.Builder chatClientBuilder;
    private final ConversationMemoryService memoryService;

    private static final String SYSTEM = """
            你是生产质控智能助手，帮助质检人员：
            1. 查询指定生产线的不良品率和质检数据
            2. 分析质量问题的主要原因和趋势
            3. 生成质检报告摘要
            4. 预警质量异常风险
            5. 对比不同时段的质量数据
            
            回答要数据准确、重点突出，异常情况用醒目方式标注。
            当前用户: {userId}
            """;

    public String chat(String userId, String sessionId, String message) {
        return chatClientBuilder
                .defaultSystem(SYSTEM)
                .defaultAdvisors(new SimpleLoggerAdvisor(), MessageChatMemoryAdvisor.builder(
                        memoryService.getOrCreateMemory(sessionId)).build())
                .build()
                .prompt()
                .system(s -> s.param("userId", userId))
                .user(message)
                .call()
                .content();
    }

    public Flux<String> chatStream(String userId, String sessionId, String message) {
        return chatClientBuilder
                .defaultSystem(SYSTEM)
                .defaultAdvisors(new SimpleLoggerAdvisor(), MessageChatMemoryAdvisor.builder(
                        memoryService.getOrCreateMemory(sessionId)).build())
                .build()
                .prompt()
                .system(s -> s.param("userId", userId))
                .user(message)
                .stream()
                .content();
    }
}
