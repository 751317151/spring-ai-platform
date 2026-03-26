package com.huah.ai.platform.agent.dto;

import lombok.Data;

@Data
public class AgentChatRequest {
    private String message;
    private SessionConfigRequest sessionConfig;
}
