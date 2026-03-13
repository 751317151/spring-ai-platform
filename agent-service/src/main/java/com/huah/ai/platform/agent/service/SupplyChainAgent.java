package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SupplyChainAgent {
    private final ChatClient.Builder b;
    private final ConversationMemoryService mem;

    public String chat(String userId, String sessionId, String message) {
        return b.defaultSystem("你是供应链智能助手，帮助采购和仓储人员：库存查询、采购订单追踪、供应商管理、交期预测、补货建议。用户:{userId}")
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(mem.getOrCreateMemory(sessionId)).build())
                .build()
                .prompt().system(s -> s.param("userId", userId)).user(message).call().content();
    }
}
