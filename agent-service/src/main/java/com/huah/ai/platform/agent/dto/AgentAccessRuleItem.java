package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentAccessRuleItem {
    private String code;
    private String name;
    private String category;
    private boolean enabled;
    private boolean authorized;
    private String status;
    private String reason;
    private String reasonCode;
    private String reasonMessage;
    private String resource;
    private String detail;
}
