package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class RdAssistantAgent extends BaseAssistantAgent {

    public RdAssistantAgent(@Qualifier("rdChatClient") ChatClient chatClient,
                            ConversationMemoryService conversationMemoryService,
                            SessionRuntimeInstructionBuilder sessionRuntimeInstructionBuilder) {
        super("rd", chatClient, conversationMemoryService, sessionRuntimeInstructionBuilder);
    }
}
