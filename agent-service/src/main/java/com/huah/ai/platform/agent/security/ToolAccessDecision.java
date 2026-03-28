package com.huah.ai.platform.agent.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolAccessDecision {
    private boolean allowed;
    private String reasonCode;
    private String reasonMessage;
    private String resource;
    private String detail;
}
