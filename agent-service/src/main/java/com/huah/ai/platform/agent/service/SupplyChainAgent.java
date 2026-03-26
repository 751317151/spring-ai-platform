package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class SupplyChainAgent extends BaseAssistantAgent {

    public SupplyChainAgent(@Qualifier("supplyChainChatClient") ChatClient chatClient,
                            ConversationMemoryService conversationMemoryService,
                            SessionRuntimeInstructionBuilder sessionRuntimeInstructionBuilder) {
        super("supply-chain", chatClient, conversationMemoryService, sessionRuntimeInstructionBuilder);
    }
}
