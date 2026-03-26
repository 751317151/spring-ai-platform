package com.huah.ai.platform.agent.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionConfigResponse {
    private String model;
    private Double temperature;
    private Integer maxContextMessages;
    private Boolean knowledgeEnabled;
    private String systemPromptTemplate;
    private Long updatedAt;
}
