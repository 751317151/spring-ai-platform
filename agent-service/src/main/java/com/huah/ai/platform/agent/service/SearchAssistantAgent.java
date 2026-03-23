package com.huah.ai.platform.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class SearchAssistantAgent extends BaseAssistantAgent {

    public SearchAssistantAgent(@Qualifier("searchChatClient") ChatClient chatClient) {
        super("search", chatClient);
    }
}
