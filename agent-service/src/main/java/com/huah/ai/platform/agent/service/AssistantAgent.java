package com.huah.ai.platform.agent.service;

import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

public interface AssistantAgent {

    String getAgentType();

    Flux<ChatResponse> chatStream(String userId, String sessionId, String message);
}
