package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentWorkbenchSummaryResponse {
    private String agentType;
    private String windowLabel;
    private long totalCalls;
    private long failureCalls;
    private double successRate;
    private long avgLatencyMs;
    private long toolCallCount;
    private long toolFailureCount;
    private long avgToolLatencyMs;
    private String slowestToolName;
    private long slowestToolLatencyMs;
    private long recentTraceCount;
    private String latestTraceId;
    private String latestErrorMessage;
    private AgentWorkbenchHealthSummary healthSummary;
    private AgentRuntimePolicySummary runtimePolicySummary;
    private List<AgentWorkbenchTrendPoint> last24hTrend;
    private List<AgentWorkbenchTrendPoint> last7dTrend;
    private List<AgentWorkbenchTrendPoint> last4wTrend;
    private List<AgentWorkbenchToolRankItem> toolRanking;
    private List<AgentWorkbenchErrorTypeItem> errorTypes;
    private List<AgentWorkbenchChangeItem> recentChanges;
    private String weeklyDigest;
    private List<AgentWorkbenchFailureItem> recentFailures;
}
