package com.huah.ai.platform.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class QcAssistantAgent extends BaseAssistantAgent {

    public QcAssistantAgent(@Qualifier("qcChatClient") ChatClient chatClient) {
        super("qc", chatClient);
    }
}
