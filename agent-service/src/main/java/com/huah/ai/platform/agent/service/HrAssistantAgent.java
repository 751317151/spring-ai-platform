package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class HrAssistantAgent extends BaseAssistantAgent {

    public HrAssistantAgent(@Qualifier("hrChatClient") ChatClient chatClient,
                            ConversationMemoryService conversationMemoryService,
                            SessionRuntimeInstructionBuilder sessionRuntimeInstructionBuilder) {
        super("hr", chatClient, conversationMemoryService, sessionRuntimeInstructionBuilder);
    }
}
