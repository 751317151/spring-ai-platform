package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMetadataItem {
    private String agentType;
    private String name;
    private String icon;
    private String color;
    private String description;
    private String defaultModel;
    private Double defaultTemperature;
    private Integer defaultMaxContextMessages;
    private boolean supportsKnowledge;
    private boolean supportsTools;
    private boolean supportsMultiAgentMode;
    private boolean supportsMultiStepRecovery;
    private boolean registered;
}
