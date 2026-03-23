package com.huah.ai.platform.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class SupplyChainAgent extends BaseAssistantAgent {

    public SupplyChainAgent(@Qualifier("supplyChainChatClient") ChatClient chatClient) {
        super("supply-chain", chatClient);
    }
}
