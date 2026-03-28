package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentWorkbenchFailureItem {
    private String traceId;
    private String sessionId;
    private String userId;
    private String summary;
    private String errorMessage;
    private Long latencyMs;
    private LocalDateTime createdAt;
}
