package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.audit.AiAuditLogMapper;
import com.huah.ai.platform.agent.audit.AiToolAuditLogMapper;
import com.huah.ai.platform.agent.config.AgentLifecycleProperties;
import com.huah.ai.platform.agent.dto.AgentLogArchiveManifestInfo;
import com.huah.ai.platform.agent.dto.AgentLogCleanupResponse;
import com.huah.ai.platform.agent.dto.AgentLogLifecycleSummaryResponse;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionStepMapper;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionTraceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLogLifecycleServiceTest {

    private AiAuditLogMapper auditLogMapper;
    private AiToolAuditLogMapper toolAuditLogMapper;
    private MultiAgentExecutionTraceMapper traceMapper;
    private MultiAgentExecutionStepMapper stepMapper;
    private AgentLogArchiveService agentLogArchiveService;
    private AgentLogLifecycleService agentLogLifecycleService;

    @BeforeEach
    void setUp() {
        auditLogMapper = mock(AiAuditLogMapper.class);
        toolAuditLogMapper = mock(AiToolAuditLogMapper.class);
        traceMapper = mock(MultiAgentExecutionTraceMapper.class);
        stepMapper = mock(MultiAgentExecutionStepMapper.class);
        agentLogArchiveService = mock(AgentLogArchiveService.class);

        AgentLifecycleProperties properties = new AgentLifecycleProperties();
        properties.getAudit().setArchiveAfterDays(14);
        properties.getAudit().setDeleteAfterDays(60);
        properties.getToolAudit().setArchiveAfterDays(14);
        properties.getToolAudit().setDeleteAfterDays(45);
        properties.getTrace().setArchiveAfterDays(7);
        properties.getTrace().setDeleteAfterDays(30);

        agentLogLifecycleService = new AgentLogLifecycleService(
                auditLogMapper,
                toolAuditLogMapper,
                traceMapper,
                stepMapper,
                properties,
                agentLogArchiveService
        );
    }

    @Test
    void shouldBuildLifecycleSummary() {
        when(auditLogMapper.countByAgentType("rd")).thenReturn(20L);
        when(auditLogMapper.countByAgentTypeBefore(eq("rd"), any())).thenReturn(8L, 3L);
        when(toolAuditLogMapper.countByAgentType("rd")).thenReturn(12L);
        when(toolAuditLogMapper.countByAgentTypeBefore(eq("rd"), any())).thenReturn(5L, 1L);
        when(traceMapper.countByAgentType("rd")).thenReturn(9L);
        when(traceMapper.countByAgentTypeBefore(eq("rd"), any())).thenReturn(4L, 2L);
        when(agentLogArchiveService.findLatestManifestInfo("rd")).thenReturn(AgentLogArchiveManifestInfo.builder()
                .manifestDir("data/agent-lifecycle-archive")
                .bundleDir("data/agent-lifecycle-archive/rd-20260327010101")
                .manifestPath("data/agent-lifecycle-archive/rd-20260327010101.json")
                .generatedAt("2026-03-27T01:01:01")
                .exportedRecordCount(12)
                .enabled(true)
                .build());

        AgentLogLifecycleSummaryResponse response = agentLogLifecycleService.buildSummary("rd");

        assertEquals("rd", response.getAgentType());
        assertEquals(24L, response.getTotalActiveCount());
        assertEquals(11L, response.getTotalArchiveCandidateCount());
        assertEquals(6L, response.getTotalDeleteCandidateCount());
        assertEquals(12L, response.getTotalColdDataCount());
        assertEquals(false, response.isAutomationEnabled());
        assertEquals(true, response.isAutomationDryRun());
        assertEquals(3600000L, response.getAutomationIntervalMs());
        assertEquals("data/agent-lifecycle-archive", response.getArchiveManifestDir());
        assertEquals("data/agent-lifecycle-archive/rd-20260327010101", response.getLastArchiveBundleDir());
        assertEquals("data/agent-lifecycle-archive/rd-20260327010101.json", response.getLastArchiveManifestPath());
        assertEquals("2026-03-27T01:01:01", response.getLastArchiveManifestAt());
        assertEquals(12L, response.getLastArchiveExportedRecordCount());
        assertEquals(3, response.getBuckets().size());
        assertEquals("audit", response.getBuckets().get(0).getType());
        assertEquals(12L, response.getBuckets().get(0).getActiveCount());
        assertEquals(5L, response.getBuckets().get(0).getArchiveCandidateCount());
        assertEquals(3L, response.getBuckets().get(0).getDeleteCandidateCount());
        assertTrue(response.getSummary().contains("active=24"));
        assertTrue(response.getSummary().contains("cold=12"));
    }

    @Test
    void shouldOnlyPreviewCleanupWhenDryRun() {
        when(auditLogMapper.countByAgentTypeBefore(eq("rd"), any())).thenReturn(3L);
        when(toolAuditLogMapper.countByAgentTypeBefore(eq("rd"), any())).thenReturn(2L);
        when(traceMapper.countByAgentTypeBefore(eq("rd"), any())).thenReturn(1L);
        when(stepMapper.countByAgentTypeBefore(eq("rd"), any())).thenReturn(4L);

        AgentLogCleanupResponse response = agentLogLifecycleService.cleanup("rd", true);

        assertTrue(response.isDryRun());
        assertEquals(3L, response.getDeletedAuditLogs());
        assertEquals(2L, response.getDeletedToolAuditLogs());
        assertEquals(1L, response.getDeletedTraces());
        assertEquals(4L, response.getDeletedTraceSteps());
        verify(stepMapper, never()).deleteByAgentTypeBefore(eq("rd"), any());
        verify(traceMapper, never()).deleteByAgentTypeBefore(eq("rd"), any());
        verify(toolAuditLogMapper, never()).deleteByAgentTypeBefore(eq("rd"), any());
        verify(auditLogMapper, never()).deleteByAgentTypeBefore(eq("rd"), any());
    }

    @Test
    void shouldDeleteInExpectedOrderWhenCleanupExecuted() {
        when(auditLogMapper.countByAgentTypeBefore(eq("rd"), any())).thenReturn(3L);
        when(toolAuditLogMapper.countByAgentTypeBefore(eq("rd"), any())).thenReturn(2L);
        when(traceMapper.countByAgentTypeBefore(eq("rd"), any())).thenReturn(1L);
        when(stepMapper.countByAgentTypeBefore(eq("rd"), any())).thenReturn(4L);

        agentLogLifecycleService.cleanup("rd", false);

        InOrder inOrder = inOrder(stepMapper, traceMapper, toolAuditLogMapper, auditLogMapper);
        inOrder.verify(stepMapper).deleteByAgentTypeBefore(eq("rd"), any());
        inOrder.verify(traceMapper).deleteByAgentTypeBefore(eq("rd"), any());
        inOrder.verify(toolAuditLogMapper).deleteByAgentTypeBefore(eq("rd"), any());
        inOrder.verify(auditLogMapper).deleteByAgentTypeBefore(eq("rd"), any());
    }
}
