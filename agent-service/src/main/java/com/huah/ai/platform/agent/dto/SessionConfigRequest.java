package com.huah.ai.platform.agent.dto;

import lombok.Data;

@Data
public class SessionConfigRequest {
    private String model;
    private Double temperature;
    private Integer maxContextMessages;
    private Boolean knowledgeEnabled;
    private String systemPromptTemplate;
}
