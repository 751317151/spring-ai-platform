package com.huah.ai.platform.agent.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.huah.ai.platform.agent.audit.AiAuditLog;
import com.huah.ai.platform.agent.audit.AiAuditLogMapper;
import com.huah.ai.platform.agent.audit.AiToolAuditLog;
import com.huah.ai.platform.agent.audit.AiToolAuditLogMapper;
import com.huah.ai.platform.agent.config.AgentLifecycleProperties;
import com.huah.ai.platform.agent.dto.AgentLogArchiveManifest;
import com.huah.ai.platform.agent.dto.AgentLogArchiveDetailResponse;
import com.huah.ai.platform.agent.dto.AgentLogArchiveManifestInfo;
import com.huah.ai.platform.agent.dto.AgentLogArchivePreviewResponse;
import com.huah.ai.platform.agent.dto.AgentArchivedTraceLookupResponse;
import com.huah.ai.platform.agent.dto.AgentLogLifecycleSummaryResponse;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionTrace;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionTraceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLogArchiveServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldWriteManifestAndLoadLatestInfo() throws Exception {
        AiAuditLogMapper auditLogMapper = mock(AiAuditLogMapper.class);
        AiToolAuditLogMapper toolAuditLogMapper = mock(AiToolAuditLogMapper.class);
        MultiAgentExecutionTraceMapper traceMapper = mock(MultiAgentExecutionTraceMapper.class);

        AgentLifecycleProperties properties = new AgentLifecycleProperties();
        properties.getArchive().setManifestDir(tempDir.toString());
        properties.getArchive().setSampleLimit(2);
        properties.getArchive().setExportBatchSize(2);

        when(auditLogMapper.selectArchiveCandidates(eq("rd"), any(), eq(2))).thenReturn(List.of(
                AiAuditLog.builder()
                        .id("audit-1")
                        .traceId("trace-a1")
                        .sessionId("session-a1")
                        .userMessage("old request")
                        .createdAt(LocalDateTime.of(2026, 3, 1, 10, 0))
                        .build()
        ));
        when(auditLogMapper.selectArchiveCandidatesBatch(eq("rd"), any(), eq(2), eq(0L))).thenReturn(List.of(
                AiAuditLog.builder().id("audit-1").traceId("trace-a1").sessionId("session-a1").userMessage("old request").createdAt(LocalDateTime.of(2026, 3, 1, 10, 0)).build(),
                AiAuditLog.builder().id("audit-2").traceId("trace-a2").sessionId("session-a2").userMessage("older request").createdAt(LocalDateTime.of(2026, 3, 1, 11, 0)).build()
        ));
        when(auditLogMapper.selectArchiveCandidatesBatch(eq("rd"), any(), eq(2), eq(2L))).thenReturn(List.of());
        when(toolAuditLogMapper.selectArchiveCandidates(eq("rd"), any(), eq(2))).thenReturn(List.of(
                AiToolAuditLog.builder()
                        .id("tool-1")
                        .traceId("trace-t1")
                        .sessionId("session-t1")
                        .toolName("queryJira")
                        .inputSummary("tool input")
                        .createdAt(LocalDateTime.of(2026, 3, 2, 11, 0))
                        .build()
        ));
        when(toolAuditLogMapper.selectArchiveCandidatesBatch(eq("rd"), any(), eq(2), eq(0L))).thenReturn(List.of(
                AiToolAuditLog.builder().id("tool-1").traceId("trace-t1").sessionId("session-t1").toolName("queryJira").inputSummary("tool input").createdAt(LocalDateTime.of(2026, 3, 2, 11, 0)).build()
        ));
        when(traceMapper.selectArchiveCandidates(eq("rd"), any(), eq(2))).thenReturn(List.of(
                MultiAgentExecutionTrace.builder()
                        .id("trace-1")
                        .traceId("trace-m1")
                        .sessionId("session-m1")
                        .requestSummary("multi request")
                        .createdAt(LocalDateTime.of(2026, 3, 3, 12, 0))
                        .build()
        ));
        when(traceMapper.selectArchiveCandidatesBatch(eq("rd"), any(), eq(2), eq(0L))).thenReturn(List.of(
                MultiAgentExecutionTrace.builder().id("trace-1").traceId("trace-m1").sessionId("session-m1").requestSummary("multi request").createdAt(LocalDateTime.of(2026, 3, 3, 12, 0)).build()
        ));

        AgentLogArchiveService service = new AgentLogArchiveService(
                auditLogMapper,
                toolAuditLogMapper,
                traceMapper,
                properties,
                JsonMapper.builder().findAndAddModules().build()
        );

        AgentLogLifecycleSummaryResponse summary = AgentLogLifecycleSummaryResponse.builder()
                .agentType("rd")
                .totalArchiveCandidateCount(3)
                .totalDeleteCandidateCount(1)
                .summary("active=1, archiveCandidates=3, deleteCandidates=1")
                .buckets(List.of())
                .build();

        AgentLogArchiveManifest manifest = service.createManifest("rd", summary, false);

        assertNotNull(manifest.getManifestPath());
        assertTrue(Files.exists(Path.of(manifest.getManifestPath())));
        assertTrue(Files.isDirectory(Path.of(manifest.getBundleDir())));
        assertEquals(3, manifest.getArtifacts().size());
        assertEquals(4L, manifest.getExportedRecordCount());
        assertTrue(Files.exists(Path.of(manifest.getArtifacts().get(0).getPath())));
        assertEquals(2L, manifest.getArtifacts().get(0).getRecordCount());
        assertEquals(3, manifest.getSamples().size());
        assertEquals("audit", manifest.getSamples().get(0).getType());
        assertEquals("tool-audit", manifest.getSamples().get(1).getType());
        assertEquals("trace", manifest.getSamples().get(2).getType());

        AgentLogArchiveManifestInfo info = service.findLatestManifestInfo("rd");
        assertEquals(tempDir.toAbsolutePath().toString(), info.getManifestDir());
        assertEquals(Path.of(manifest.getBundleDir()).toAbsolutePath().toString(), info.getBundleDir());
        assertEquals(Path.of(manifest.getManifestPath()).toAbsolutePath().toString(), info.getManifestPath());
        assertNotNull(info.getGeneratedAt());
        assertEquals(4L, info.getExportedRecordCount());

        AgentLogArchiveDetailResponse detail = service.loadLatestManifest("rd");
        assertEquals("rd", detail.getAgentType());
        assertEquals(2, detail.getArtifacts().stream().filter(item -> "audit".equals(item.getType())).findFirst().orElseThrow().getRecordCount());
        assertEquals(3, detail.getArtifacts().size());
        assertEquals(3, detail.getSamples().size());
        assertEquals(4L, detail.getExportedRecordCount());
        assertEquals(4L, detail.getColdDataCount());
        assertEquals(2, detail.getSampleLimit());
        assertEquals(2, detail.getExportBatchSize());
        assertEquals(3, detail.getOperationHints().size());

        AgentLogArchivePreviewResponse preview = service.previewLatestArtifact("rd", "audit", 2);
        assertEquals("rd", preview.getAgentType());
        assertEquals("audit", preview.getArtifactType());
        assertEquals(2, preview.getPreviewLimit());
        assertEquals(2, preview.getItems().size());
        assertTrue(preview.getItems().get(0).getContent().contains("\"id\":\"audit-1\""));

        AgentArchivedTraceLookupResponse archivedTrace = service.findArchivedTrace("rd", "trace-m1");
        assertTrue(archivedTrace.isFound());
        assertTrue(archivedTrace.isReplayable());
        assertEquals("trace", archivedTrace.getArtifactType());
        assertEquals("trace-m1", archivedTrace.getTraceId());
        assertNotNull(archivedTrace.getTrace());
        assertEquals("trace-m1", service.loadArchivedTraceRecord("rd", "trace-m1").getTraceId());
    }
}
