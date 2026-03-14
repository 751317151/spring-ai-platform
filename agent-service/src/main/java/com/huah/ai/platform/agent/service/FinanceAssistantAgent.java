package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Service
@RequiredArgsConstructor
public class FinanceAssistantAgent {
    private final ChatClient.Builder b;
    private final ConversationMemoryService mem;

    private static final String SYSTEM = "你是财务分析智能助手，帮助财务人员：报表查询、收支分析、预算执行分析、报销申请。用户:{userId}";

    public String chat(String userId, String sessionId, String message) {
        return b.defaultSystem(SYSTEM)
                .defaultAdvisors(new SimpleLoggerAdvisor(), MessageChatMemoryAdvisor.builder(mem.getOrCreateMemory(sessionId)).build())
                .build()
                .prompt().system(s -> s.param("userId", userId)).user(message)
                .advisors(a -> a.param(CONVERSATION_ID, sessionId)).call().content();
    }

    public Flux<String> chatStream(String userId, String sessionId, String message) {
        return b.defaultSystem(SYSTEM)
                .defaultAdvisors(new SimpleLoggerAdvisor(), MessageChatMemoryAdvisor.builder(mem.getOrCreateMemory(sessionId)).build())
                .build()
                .prompt().system(s -> s.param("userId", userId)).user(message)
                .advisors(a -> a.param(CONVERSATION_ID, sessionId)).stream().content();
    }
}
