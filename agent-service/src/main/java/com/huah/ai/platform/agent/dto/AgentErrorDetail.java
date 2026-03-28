package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentErrorDetail {
    private String errorCode;
    private String errorCategory;
    private String reasonCode;
    private String resource;
    private String detail;
    private String traceId;
    private Boolean recoverable;
}
