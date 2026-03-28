package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentLogCleanupResponse {
    private String agentType;
    private boolean dryRun;
    private long deletedAuditLogs;
    private long deletedToolAuditLogs;
    private long deletedTraceSteps;
    private long deletedTraces;
    private String summary;
}
