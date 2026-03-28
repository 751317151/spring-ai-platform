package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.config.ToolsProperties;
import com.huah.ai.platform.agent.dto.AgentRuntimePolicySummary;
import com.huah.ai.platform.agent.security.ToolSecurityService;
import com.huah.ai.platform.agent.tools.InternalApiTools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentRuntimePolicyService {

    private static final String WILDCARD = "*";
    private static final long DEFAULT_REQUEST_TIMEOUT_MS = 60000L;
    private static final long DEFAULT_STREAM_TIMEOUT_MS = 180000L;

    private final ToolSecurityService toolSecurityService;
    private final ToolsProperties toolsProperties;
    private final InternalApiTools internalApiTools;
    private final McpServerCatalogService mcpServerCatalogService;
    private final com.huah.ai.platform.agent.security.AgentAccessChecker agentAccessChecker;
    private final AgentRuntimeIsolationService agentRuntimeIsolationService;

    public AgentRuntimePolicySummary build(String agentType) {
        boolean securityEnabled = toolSecurityService.isSecurityEnabled();
        List<String> allowedTools = toolSecurityService.getAllowedTools(agentType);
        List<String> allowedConnectors = toolSecurityService.getAllowedConnectors(agentType);
        List<String> allowedMcpServers = toolSecurityService.getAllowedMcpServers(agentType);
        List<String> dataScopes = toolSecurityService.getAllowedDataScopes(agentType);
        List<String> allowedDataSources = toolSecurityService.getAllowedDataSources(agentType);
        List<String> crossSchemaAccess = toolSecurityService.getAllowedCrossSchemaAccess(agentType);

        boolean wildcardToolAccess = hasWildcard(allowedTools);
        boolean wildcardConnectorAccess = hasWildcard(allowedConnectors);
        boolean wildcardMcpAccess = hasWildcard(allowedMcpServers);
        boolean wildcardDataAccess = hasWildcard(dataScopes);
        boolean wildcardDataSourceAccess = hasWildcard(allowedDataSources);
        boolean wildcardCrossSchemaAccess = hasWildcard(crossSchemaAccess);

        boolean connectorIsolationEnabled = hasConnectorResourceIsolation(agentType);
        boolean mcpToolIsolationEnabled = hasMcpToolIsolation(agentType);
        boolean dataScopeIsolationEnabled = !dataScopes.isEmpty() && !wildcardDataAccess;
        boolean dataSourceIsolationEnabled = !allowedDataSources.isEmpty() && !wildcardDataSourceAccess;
        boolean crossSchemaAccessControlled = !crossSchemaAccess.isEmpty() && !wildcardCrossSchemaAccess;
        int maxConcurrency = agentRuntimeIsolationService.getMaxConcurrency(agentType);
        int maxQueueDepth = agentRuntimeIsolationService.getMaxQueueDepth(agentType);
        int currentActiveRequests = agentRuntimeIsolationService.currentActive(agentType);
        int currentWaitingRequests = agentRuntimeIsolationService.currentWaiting(agentType);
        int dailyTokenLimit = agentAccessChecker.getDailyTokenLimit(agentType);
        long queueWaitTimeoutMs = agentRuntimeIsolationService.getQueueWaitTimeoutMs(agentType, 0L);
        long requestTimeoutMs = agentRuntimeIsolationService.getRequestTimeoutMs(agentType, DEFAULT_REQUEST_TIMEOUT_MS);
        long streamTimeoutMs = agentRuntimeIsolationService.getStreamTimeoutMs(agentType, DEFAULT_STREAM_TIMEOUT_MS);
        boolean concurrencyIsolationEnabled = maxConcurrency > 0;
        boolean queueGovernanceEnabled = concurrencyIsolationEnabled && maxQueueDepth > 0 && queueWaitTimeoutMs > 0;

        int restrictedResourceCount = countRestrictedResources(
                agentType,
                connectorIsolationEnabled,
                mcpToolIsolationEnabled,
                dataScopeIsolationEnabled,
                dataSourceIsolationEnabled,
                crossSchemaAccessControlled,
                concurrencyIsolationEnabled
        );
        int riskCount = 0;
        if (!securityEnabled) {
            riskCount += 2;
        }
        if (wildcardToolAccess) {
            riskCount++;
        }
        if (wildcardConnectorAccess) {
            riskCount++;
        }
        if (wildcardMcpAccess) {
            riskCount++;
        }
        if (wildcardDataAccess) {
            riskCount++;
        }
        if (!dataSourceIsolationEnabled) {
            riskCount++;
        }
        if (!crossSchemaAccessControlled) {
            riskCount++;
        }
        if (!concurrencyIsolationEnabled) {
            riskCount++;
        }
        if (concurrencyIsolationEnabled && !queueGovernanceEnabled) {
            riskCount++;
        }

        String riskLevel = riskCount >= 4 ? "high" : riskCount >= 2 ? "medium" : "low";
        List<String> highlights = new ArrayList<>();
        highlights.add("connectorIsolation=" + (connectorIsolationEnabled ? "on" : "off"));
        highlights.add("mcpToolIsolation=" + (mcpToolIsolationEnabled ? "on" : "off"));
        highlights.add("dataScopeIsolation=" + (dataScopeIsolationEnabled ? "on" : "off"));
        highlights.add("dataSourceIsolation=" + (dataSourceIsolationEnabled ? "on" : "off"));
        highlights.add("crossSchemaControl=" + (crossSchemaAccessControlled ? "on" : "off"));
        highlights.add("concurrencyIsolation=" + (concurrencyIsolationEnabled ? "on" : "off"));
        highlights.add("queueGovernance=" + (queueGovernanceEnabled ? "on" : "off"));
        highlights.add("dailyTokenLimit=" + (dailyTokenLimit == Integer.MAX_VALUE ? "unbounded" : dailyTokenLimit));
        String summary = "security=" + (securityEnabled ? "on" : "off")
                + ", connectorIsolation=" + (connectorIsolationEnabled ? "on" : "off")
                + ", mcpToolIsolation=" + (mcpToolIsolationEnabled ? "on" : "off")
                + ", dataScopeIsolation=" + (dataScopeIsolationEnabled ? "on" : "off")
                + ", dataSourceIsolation=" + (dataSourceIsolationEnabled ? "on" : "off")
                + ", crossSchemaControl=" + (crossSchemaAccessControlled ? "on" : "off")
                + ", concurrencyIsolation=" + (concurrencyIsolationEnabled ? "on" : "off")
                + ", queueGovernance=" + (queueGovernanceEnabled ? "on" : "off")
                + ", maxConcurrency=" + maxConcurrency
                + ", maxQueueDepth=" + maxQueueDepth
                + ", activeRequests=" + currentActiveRequests
                + ", waitingRequests=" + currentWaitingRequests
                + ", queueWaitTimeoutMs=" + queueWaitTimeoutMs
                + ", requestTimeoutMs=" + requestTimeoutMs
                + ", streamTimeoutMs=" + streamTimeoutMs
                + ", restrictedResources=" + restrictedResourceCount
                + ", riskLevel=" + riskLevel;

        return AgentRuntimePolicySummary.builder()
                .securityEnabled(securityEnabled)
                .connectorResourceIsolationEnabled(connectorIsolationEnabled)
                .mcpToolIsolationEnabled(mcpToolIsolationEnabled)
                .dataScopeIsolationEnabled(dataScopeIsolationEnabled)
                .dataSourceIsolationEnabled(dataSourceIsolationEnabled)
                .crossSchemaAccessControlled(crossSchemaAccessControlled)
                .concurrencyIsolationEnabled(concurrencyIsolationEnabled)
                .queueGovernanceEnabled(queueGovernanceEnabled)
                .wildcardToolAccess(wildcardToolAccess)
                .wildcardConnectorAccess(wildcardConnectorAccess)
                .wildcardMcpAccess(wildcardMcpAccess)
                .wildcardDataAccess(wildcardDataAccess)
                .wildcardDataSourceAccess(wildcardDataSourceAccess)
                .wildcardCrossSchemaAccess(wildcardCrossSchemaAccess)
                .currentActiveRequests(currentActiveRequests)
                .currentWaitingRequests(currentWaitingRequests)
                .maxConcurrency(maxConcurrency)
                .maxQueueDepth(maxQueueDepth)
                .dailyTokenLimit(dailyTokenLimit)
                .queueWaitTimeoutMs(queueWaitTimeoutMs)
                .requestTimeoutMs(requestTimeoutMs)
                .streamTimeoutMs(streamTimeoutMs)
                .restrictedResourceCount(restrictedResourceCount)
                .riskCount(riskCount)
                .riskLevel(riskLevel)
                .summary(summary)
                .highlights(highlights)
                .build();
    }

    private boolean hasConnectorResourceIsolation(String agentType) {
        for (String connectorCode : internalApiTools.listEnabledConnectorCodes()) {
            List<String> prefixes = toolSecurityService.getAllowedConnectorResourcePrefixes(
                    agentType,
                    connectorCode,
                    toolsProperties.getInternalApi().getConnectors().get(connectorCode) != null
                            ? toolsProperties.getInternalApi().getConnectors().get(connectorCode).getAllowedPathPrefixes()
                            : List.of()
            );
            if (!prefixes.isEmpty() && !hasWildcard(prefixes)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMcpToolIsolation(String agentType) {
        return mcpServerCatalogService.listAllServers(agentType).stream()
                .map(item -> toolSecurityService.getAllowedMcpTools(agentType, item.getCode()))
                .anyMatch(values -> !values.isEmpty() && !hasWildcard(values));
    }

    private int countRestrictedResources(String agentType,
                                         boolean connectorIsolationEnabled,
                                         boolean mcpToolIsolationEnabled,
                                         boolean dataScopeIsolationEnabled,
                                         boolean dataSourceIsolationEnabled,
                                         boolean crossSchemaAccessControlled,
                                         boolean concurrencyIsolationEnabled) {
        int count = 0;
        if (connectorIsolationEnabled) {
            count += (int) internalApiTools.listEnabledConnectorCodes().stream()
                    .filter(code -> {
                        List<String> prefixes = toolSecurityService.getAllowedConnectorResourcePrefixes(
                                agentType,
                                code,
                                toolsProperties.getInternalApi().getConnectors().get(code) != null
                                        ? toolsProperties.getInternalApi().getConnectors().get(code).getAllowedPathPrefixes()
                                        : List.of()
                        );
                        return !prefixes.isEmpty() && !hasWildcard(prefixes);
                    })
                    .count();
        }
        if (mcpToolIsolationEnabled) {
            count += (int) mcpServerCatalogService.listAllServers(agentType).stream()
                    .map(item -> toolSecurityService.getAllowedMcpTools(agentType, item.getCode()))
                    .filter(values -> !values.isEmpty() && !hasWildcard(values))
                    .count();
        }
        if (dataScopeIsolationEnabled) {
            count += (int) toolSecurityService.getAllowedDataScopes(agentType).stream()
                    .filter(scope -> scope != null && !scope.isBlank() && !WILDCARD.equals(scope.trim()))
                    .count();
        }
        if (dataSourceIsolationEnabled) {
            count += (int) toolSecurityService.getAllowedDataSources(agentType).stream()
                    .filter(scope -> scope != null && !scope.isBlank() && !WILDCARD.equals(scope.trim()))
                    .count();
        }
        if (crossSchemaAccessControlled) {
            count += (int) toolSecurityService.getAllowedCrossSchemaAccess(agentType).stream()
                    .filter(scope -> scope != null && !scope.isBlank() && !WILDCARD.equals(scope.trim()))
                    .count();
        }
        if (concurrencyIsolationEnabled) {
            count += 1;
        }
        return count;
    }

    private boolean hasWildcard(List<String> values) {
        return values.stream().anyMatch(value -> WILDCARD.equals(value));
    }
}
