package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class WeatherAssistantAgent extends BaseAssistantAgent {

    public WeatherAssistantAgent(@Qualifier("weatherChatClient") ChatClient chatClient,
                                 ConversationMemoryService conversationMemoryService,
                                 SessionRuntimeInstructionBuilder sessionRuntimeInstructionBuilder) {
        super("weather", chatClient, conversationMemoryService, sessionRuntimeInstructionBuilder);
    }
}
