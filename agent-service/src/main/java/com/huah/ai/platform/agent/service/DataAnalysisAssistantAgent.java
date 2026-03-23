package com.huah.ai.platform.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class DataAnalysisAssistantAgent extends BaseAssistantAgent {

    public DataAnalysisAssistantAgent(@Qualifier("dataAnalysisChatClient") ChatClient chatClient) {
        super("data-analysis", chatClient);
    }
}
