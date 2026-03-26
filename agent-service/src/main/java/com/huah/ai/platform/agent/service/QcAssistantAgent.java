package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class QcAssistantAgent extends BaseAssistantAgent {

    public QcAssistantAgent(@Qualifier("qcChatClient") ChatClient chatClient,
                            ConversationMemoryService conversationMemoryService,
                            SessionRuntimeInstructionBuilder sessionRuntimeInstructionBuilder) {
        super("qc", chatClient, conversationMemoryService, sessionRuntimeInstructionBuilder);
    }
}
