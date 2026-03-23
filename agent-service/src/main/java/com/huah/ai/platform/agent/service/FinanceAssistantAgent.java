package com.huah.ai.platform.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class FinanceAssistantAgent extends BaseAssistantAgent {

    public FinanceAssistantAgent(@Qualifier("financeChatClient") ChatClient chatClient) {
        super("finance", chatClient);
    }
}
