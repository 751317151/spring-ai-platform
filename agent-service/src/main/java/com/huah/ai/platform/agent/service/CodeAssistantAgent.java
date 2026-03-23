package com.huah.ai.platform.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class CodeAssistantAgent extends BaseAssistantAgent {

    public CodeAssistantAgent(@Qualifier("codeChatClient") ChatClient chatClient) {
        super("code", chatClient);
    }
}
