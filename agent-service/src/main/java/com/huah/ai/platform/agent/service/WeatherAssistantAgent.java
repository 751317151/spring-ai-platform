package com.huah.ai.platform.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class WeatherAssistantAgent extends BaseAssistantAgent {

    public WeatherAssistantAgent(@Qualifier("weatherChatClient") ChatClient chatClient) {
        super("weather", chatClient);
    }
}
