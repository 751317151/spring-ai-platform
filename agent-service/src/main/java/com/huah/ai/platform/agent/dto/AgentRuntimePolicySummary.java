package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRuntimePolicySummary {
    private boolean securityEnabled;
    private boolean connectorResourceIsolationEnabled;
    private boolean mcpToolIsolationEnabled;
    private boolean dataScopeIsolationEnabled;
    private boolean dataSourceIsolationEnabled;
    private boolean crossSchemaAccessControlled;
    private boolean concurrencyIsolationEnabled;
    private boolean queueGovernanceEnabled;
    private boolean wildcardToolAccess;
    private boolean wildcardConnectorAccess;
    private boolean wildcardMcpAccess;
    private boolean wildcardDataAccess;
    private boolean wildcardDataSourceAccess;
    private boolean wildcardCrossSchemaAccess;
    private int currentActiveRequests;
    private int currentWaitingRequests;
    private int maxConcurrency;
    private int maxQueueDepth;
    private int dailyTokenLimit;
    private long queueWaitTimeoutMs;
    private long requestTimeoutMs;
    private long streamTimeoutMs;
    private int restrictedResourceCount;
    private int riskCount;
    private String riskLevel;
    private String summary;
    private java.util.List<String> highlights;
}
