package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDefinitionUpsertRequest {
    private String agentCode;
    private String agentName;
    private String description;
    private String icon;
    private String color;
    private String systemPrompt;
    private String defaultModel;
    private String toolCodes;
    private String mcpServerCodes;
    private Boolean enabled;
    private Integer sortOrder;
    private Integer dailyTokenLimit;
    private String allowedRoles;
    private String assistantProfile;
}
