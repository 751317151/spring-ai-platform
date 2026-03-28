package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.audit.AiAuditLog;
import com.huah.ai.platform.agent.audit.AiAuditLogMapper;
import com.huah.ai.platform.agent.audit.AiToolAuditLog;
import com.huah.ai.platform.agent.audit.AiToolAuditLogMapper;
import com.huah.ai.platform.agent.dto.AgentWorkbenchErrorTypeItem;
import com.huah.ai.platform.agent.dto.AgentWorkbenchCompareResponse;
import com.huah.ai.platform.agent.dto.AgentWorkbenchSummaryResponse;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionTrace;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionTraceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentWorkbenchServiceTest {

    private AiAuditLogMapper auditLogMapper;
    private AiToolAuditLogMapper toolAuditLogMapper;
    private MultiAgentExecutionTraceMapper traceMapper;
    private AgentRuntimePolicyService agentRuntimePolicyService;
    private AgentWorkbenchService agentWorkbenchService;

    @BeforeEach
    void setUp() {
        auditLogMapper = mock(AiAuditLogMapper.class);
        toolAuditLogMapper = mock(AiToolAuditLogMapper.class);
        traceMapper = mock(MultiAgentExecutionTraceMapper.class);
        agentRuntimePolicyService = mock(AgentRuntimePolicyService.class);
        when(agentRuntimePolicyService.build("multi")).thenReturn(
                com.huah.ai.platform.agent.dto.AgentRuntimePolicySummary.builder()
                        .securityEnabled(true)
                        .connectorResourceIsolationEnabled(true)
                        .mcpToolIsolationEnabled(true)
                        .dataScopeIsolationEnabled(true)
                        .restrictedResourceCount(4)
                        .riskCount(1)
                        .riskLevel("low")
                        .summary("policy-summary")
                        .build()
        );
        agentWorkbenchService = new AgentWorkbenchService(auditLogMapper, toolAuditLogMapper, traceMapper, agentRuntimePolicyService);
    }

    @Test
    void shouldBuildWorkbenchSummaryWithTrendRankingAndErrorDistribution() {
        LocalDateTime now = LocalDateTime.now();
        List<AiAuditLog> auditLogs24h = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            auditLogs24h.add(AiAuditLog.builder()
                    .id("audit-" + i)
                    .agentType("multi")
                    .userId("u-" + i)
                    .sessionId("session-" + (i % 2))
                    .traceId("trace-" + i)
                    .userMessage("request-" + i)
                    .latencyMs(120L + i)
                    .success(i >= 3)
                    .errorMessage(i == 0 ? "permission denied" : i == 1 ? "network timeout" : i == 2 ? "model exploded" : null)
                    .createdAt(now.minusHours(i % 4))
                    .build());
        }
        List<AiToolAuditLog> toolLogs24h = List.of(
                buildToolLog("tool-1", "searchDocuments", true, null, null, 80L, "trace-1", now.minusHours(1)),
                buildToolLog("tool-2", "searchDocuments", false, "MCP_DENIED", "access denied", 90L, "trace-2", now.minusHours(1)),
                buildToolLog("tool-3", "fetchChunk", true, null, null, 60L, "trace-3", now.minusHours(2)),
                buildToolLog("tool-4", "fetchChunk", false, null, "network timeout", 120L, "trace-4", now.minusHours(2)),
                buildToolLog("tool-5", "searchDocuments", true, null, null, 110L, "trace-5", now.minusHours(3)),
                buildToolLog("tool-6", "searchDocuments", true, null, null, 70L, "trace-6", now.minusHours(3)),
                buildToolLog("tool-7", "searchDocuments", true, null, null, 65L, "trace-7", now.minusHours(4)),
                buildToolLog("tool-8", "fetchChunk", true, null, null, 75L, "trace-8", now.minusHours(4)),
                buildToolLog("tool-9", "searchDocuments", true, null, null, 78L, "trace-9", now.minusHours(5)),
                buildToolLog("tool-10", "fetchChunk", true, null, null, 82L, "trace-10", now.minusHours(5))
        );
        List<MultiAgentExecutionTrace> traces = List.of(
                MultiAgentExecutionTrace.builder()
                        .traceId("trace-latest")
                        .sessionId("session-1")
                        .userId("u-latest")
                        .agentType("multi")
                        .status("SUCCESS")
                        .createdAt(now.minusMinutes(5))
                        .build()
        );

        when(auditLogMapper.selectByAgentTypeAfter(eq("multi"), any())).thenReturn(auditLogs24h, auditLogs24h);
        when(toolAuditLogMapper.selectRecentByAgentTypeAfter(eq("multi"), any(), anyInt())).thenReturn(toolLogs24h, toolLogs24h);
        when(traceMapper.selectRecentAfter(any(), anyInt())).thenReturn(traces);

        AgentWorkbenchSummaryResponse response = agentWorkbenchService.build("multi");

        assertEquals("multi", response.getAgentType());
        assertEquals(10, response.getTotalCalls());
        assertEquals(3, response.getFailureCalls());
        assertEquals(10, response.getToolCallCount());
        assertEquals(2, response.getToolFailureCount());
        assertEquals("trace-latest", response.getLatestTraceId());
        assertEquals(24, response.getLast24hTrend().size());
        assertEquals(7, response.getLast7dTrend().size());
        assertEquals(4, response.getLast4wTrend().size());
        assertEquals("policy-summary", response.getRuntimePolicySummary().getSummary());
        assertTrue(response.getWeeklyDigest().contains("最近一周调用"));
        assertFalse(response.getRecentChanges().isEmpty());
        assertTrue(response.getToolRanking().stream().anyMatch(item ->
                "searchDocuments".equals(item.getToolName()) && item.getCallCount() == 6));
        assertTrue(response.getHealthSummary().isWarning());
        assertNotNull(response.getRecentFailures());

        AgentWorkbenchErrorTypeItem firstError = response.getErrorTypes().get(0);
        assertTrue(List.of("PERMISSION_DENIED", "MODEL_ERROR", "DEPENDENCY_ERROR", "TOOL_ERROR").contains(firstError.getType()));
        assertTrue(response.getErrorTypes().stream().anyMatch(item -> "PERMISSION_DENIED".equals(item.getType()) && item.getCount() >= 2));
        assertTrue(response.getErrorTypes().stream().anyMatch(item -> "DEPENDENCY_ERROR".equals(item.getType()) && item.getCount() >= 2));
    }

    @Test
    void shouldBuildCompareInsights() {
        LocalDateTime now = LocalDateTime.now();
        List<AiAuditLog> leftLogs = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            leftLogs.add(AiAuditLog.builder()
                    .id("left-" + i)
                    .agentType("rd")
                    .latencyMs(220L)
                    .success(i >= 4)
                    .createdAt(now.minusHours(1))
                    .build());
        }
        List<AiAuditLog> rightLogs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            rightLogs.add(AiAuditLog.builder()
                    .id("right-" + i)
                    .agentType("ops")
                    .latencyMs(90L)
                    .success(true)
                    .createdAt(now.minusHours(1))
                    .build());
        }
        when(auditLogMapper.selectByAgentTypeAfter(eq("rd"), any())).thenReturn(leftLogs, leftLogs, leftLogs);
        when(auditLogMapper.selectByAgentTypeAfter(eq("ops"), any())).thenReturn(rightLogs, rightLogs, rightLogs);
        when(toolAuditLogMapper.selectRecentByAgentTypeAfter(eq("rd"), any(), anyInt())).thenReturn(List.of(), List.of(), List.of());
        when(toolAuditLogMapper.selectRecentByAgentTypeAfter(eq("ops"), any(), anyInt())).thenReturn(List.of(), List.of(), List.of());
        when(traceMapper.selectRecentAfter(any(), anyInt())).thenReturn(List.of());
        when(agentRuntimePolicyService.build("rd")).thenReturn(com.huah.ai.platform.agent.dto.AgentRuntimePolicySummary.builder().riskCount(2).riskLevel("medium").build());
        when(agentRuntimePolicyService.build("ops")).thenReturn(com.huah.ai.platform.agent.dto.AgentRuntimePolicySummary.builder().riskCount(0).riskLevel("low").build());

        AgentWorkbenchCompareResponse response = agentWorkbenchService.compare("rd", "ops");

        assertEquals("rd", response.getLeft().getAgentType());
        assertEquals("ops", response.getRight().getAgentType());
        assertFalse(response.getMetrics().isEmpty());
        assertTrue(response.getMetrics().stream().anyMatch(item -> "failure-rate".equals(item.getKey())));
        assertTrue(response.getMetrics().stream().anyMatch(item -> "avg-latency".equals(item.getKey())));
        assertFalse(response.getInsights().isEmpty());
        assertTrue(response.getInsights().stream().anyMatch(item -> "traffic".equals(item.getType())));
        assertTrue(response.getInsights().stream().anyMatch(item -> "latency".equals(item.getType())));
        assertNotNull(response.getLeftDetail());
        assertNotNull(response.getRightDetail());
        assertEquals("33%", response.getLeftDetail().getFailureRateLabel());
        assertEquals("0%", response.getRightDetail().getFailureRateLabel());
        assertEquals("medium", response.getLeftDetail().getRiskLevel());
        assertFalse(response.getChangeComparison().isEmpty());
        assertTrue(response.getSummary().contains("primaryGap"));
    }

    private AiToolAuditLog buildToolLog(String id,
                                        String toolName,
                                        boolean success,
                                        String reasonCode,
                                        String errorMessage,
                                        long latencyMs,
                                        String traceId,
                                        LocalDateTime createdAt) {
        return AiToolAuditLog.builder()
                .id(id)
                .agentType("multi")
                .toolName(toolName)
                .success(success)
                .reasonCode(reasonCode)
                .errorMessage(errorMessage)
                .latencyMs(latencyMs)
                .traceId(traceId)
                .createdAt(createdAt)
                .build();
    }
}
