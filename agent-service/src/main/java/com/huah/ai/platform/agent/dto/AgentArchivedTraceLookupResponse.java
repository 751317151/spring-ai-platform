package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentArchivedTraceLookupResponse {
    private String agentType;
    private boolean found;
    private String artifactType;
    private String artifactPath;
    private String traceId;
    private String archivedAt;
    private String summary;
    private boolean replayable;
    private MultiAgentTraceResponse trace;
}
