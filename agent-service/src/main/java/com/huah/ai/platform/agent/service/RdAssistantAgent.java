package com.huah.ai.platform.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class RdAssistantAgent extends BaseAssistantAgent {

    public RdAssistantAgent(@Qualifier("rdChatClient") ChatClient chatClient) {
        super("rd", chatClient);
    }
}
