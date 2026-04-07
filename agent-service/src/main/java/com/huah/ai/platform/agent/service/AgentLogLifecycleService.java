package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.config.AgentLifecycleProperties;
import com.huah.ai.platform.agent.dto.AgentLogArchiveManifestInfo;
import com.huah.ai.platform.agent.dto.AgentLogCleanupResponse;
import com.huah.ai.platform.agent.dto.AgentLogLifecycleBucket;
import com.huah.ai.platform.agent.dto.AgentLogLifecycleSummaryResponse;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionStepMapper;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionTraceMapper;
import com.huah.ai.platform.common.persistence.audit.AiAuditLogMapper;
import com.huah.ai.platform.common.persistence.audit.AiToolAuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentLogLifecycleService {

    private final AiAuditLogMapper aiAuditLogMapper;
    private final AiToolAuditLogMapper aiToolAuditLogMapper;
    private final MultiAgentExecutionTraceMapper multiAgentExecutionTraceMapper;
    private final MultiAgentExecutionStepMapper multiAgentExecutionStepMapper;
    private final AgentLifecycleProperties lifecycleProperties;
    private final AgentLogArchiveService agentLogArchiveService;

    public AgentLogLifecycleSummaryResponse buildSummary(String agentType) {
        LocalDateTime now = LocalDateTime.now();
        AgentLogLifecycleBucket auditBucket = buildAuditBucket(agentType, now);
        AgentLogLifecycleBucket toolAuditBucket = buildToolAuditBucket(agentType, now);
        AgentLogLifecycleBucket traceBucket = buildTraceBucket(agentType, now);
        List<AgentLogLifecycleBucket> buckets = List.of(auditBucket, toolAuditBucket, traceBucket);

        long totalActiveCount = buckets.stream().mapToLong(AgentLogLifecycleBucket::getActiveCount).sum();
        long totalArchiveCandidateCount = buckets.stream().mapToLong(AgentLogLifecycleBucket::getArchiveCandidateCount).sum();
        long totalDeleteCandidateCount = buckets.stream().mapToLong(AgentLogLifecycleBucket::getDeleteCandidateCount).sum();
        AgentLogArchiveManifestInfo latestManifest = agentLogArchiveService.findLatestManifestInfo(agentType);
        long totalColdDataCount = latestManifest.getExportedRecordCount();

        return AgentLogLifecycleSummaryResponse.builder()
                .agentType(agentType)
                .totalActiveCount(totalActiveCount)
                .totalArchiveCandidateCount(totalArchiveCandidateCount)
                .totalDeleteCandidateCount(totalDeleteCandidateCount)
                .totalColdDataCount(totalColdDataCount)
                .automationEnabled(lifecycleProperties.getAutomation().isEnabled())
                .automationDryRun(lifecycleProperties.getAutomation().isDryRun())
                .automationIntervalMs(lifecycleProperties.getAutomation().getFixedDelayMs())
                .archiveManifestDir(latestManifest.getManifestDir())
                .lastArchiveBundleDir(latestManifest.getBundleDir())
                .lastArchiveManifestPath(latestManifest.getManifestPath())
                .lastArchiveManifestAt(latestManifest.getGeneratedAt())
                .lastArchiveExportedRecordCount(latestManifest.getExportedRecordCount())
                .buckets(buckets)
                .summary("active=" + totalActiveCount
                        + ", cold=" + totalColdDataCount
                        + ", archiveCandidates=" + totalArchiveCandidateCount
                        + ", deleteCandidates=" + totalDeleteCandidateCount)
                .build();
    }

    public AgentLogCleanupResponse cleanup(String agentType, boolean dryRun) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime auditDeleteBefore = now.minusDays(lifecycleProperties.getAudit().getDeleteAfterDays());
        LocalDateTime toolAuditDeleteBefore = now.minusDays(lifecycleProperties.getToolAudit().getDeleteAfterDays());
        LocalDateTime traceDeleteBefore = now.minusDays(lifecycleProperties.getTrace().getDeleteAfterDays());

        long deletedAuditLogs = aiAuditLogMapper.countByAgentTypeBefore(agentType, auditDeleteBefore);
        long deletedToolAuditLogs = aiToolAuditLogMapper.countByAgentTypeBefore(agentType, toolAuditDeleteBefore);
        long deletedTraces = multiAgentExecutionTraceMapper.countByAgentTypeBefore(agentType, traceDeleteBefore);
        long deletedTraceSteps = multiAgentExecutionStepMapper.countByAgentTypeBefore(agentType, traceDeleteBefore);

        if (!dryRun) {
            multiAgentExecutionStepMapper.deleteByAgentTypeBefore(agentType, traceDeleteBefore);
            multiAgentExecutionTraceMapper.deleteByAgentTypeBefore(agentType, traceDeleteBefore);
            aiToolAuditLogMapper.deleteByAgentTypeBefore(agentType, toolAuditDeleteBefore);
            aiAuditLogMapper.deleteByAgentTypeBefore(agentType, auditDeleteBefore);
        }

        return AgentLogCleanupResponse.builder()
                .agentType(agentType)
                .dryRun(dryRun)
                .deletedAuditLogs(deletedAuditLogs)
                .deletedToolAuditLogs(deletedToolAuditLogs)
                .deletedTraceSteps(deletedTraceSteps)
                .deletedTraces(deletedTraces)
                .summary((dryRun ? "dryRun" : "executed")
                        + ", auditLogs=" + deletedAuditLogs
                        + ", toolAuditLogs=" + deletedToolAuditLogs
                        + ", traceSteps=" + deletedTraceSteps
                        + ", traces=" + deletedTraces)
                .build();
    }

    private AgentLogLifecycleBucket buildAuditBucket(String agentType, LocalDateTime now) {
        return buildBucket(
                "audit",
                lifecycleProperties.getAudit().getArchiveAfterDays(),
                lifecycleProperties.getAudit().getDeleteAfterDays(),
                aiAuditLogMapper.countByAgentType(agentType),
                aiAuditLogMapper.countByAgentTypeBefore(agentType, now.minusDays(lifecycleProperties.getAudit().getArchiveAfterDays())),
                aiAuditLogMapper.countByAgentTypeBefore(agentType, now.minusDays(lifecycleProperties.getAudit().getDeleteAfterDays()))
        );
    }

    private AgentLogLifecycleBucket buildToolAuditBucket(String agentType, LocalDateTime now) {
        return buildBucket(
                "tool-audit",
                lifecycleProperties.getToolAudit().getArchiveAfterDays(),
                lifecycleProperties.getToolAudit().getDeleteAfterDays(),
                aiToolAuditLogMapper.countByAgentType(agentType),
                aiToolAuditLogMapper.countByAgentTypeBefore(agentType, now.minusDays(lifecycleProperties.getToolAudit().getArchiveAfterDays())),
                aiToolAuditLogMapper.countByAgentTypeBefore(agentType, now.minusDays(lifecycleProperties.getToolAudit().getDeleteAfterDays()))
        );
    }

    private AgentLogLifecycleBucket buildTraceBucket(String agentType, LocalDateTime now) {
        return buildBucket(
                "trace",
                lifecycleProperties.getTrace().getArchiveAfterDays(),
                lifecycleProperties.getTrace().getDeleteAfterDays(),
                multiAgentExecutionTraceMapper.countByAgentType(agentType),
                multiAgentExecutionTraceMapper.countByAgentTypeBefore(agentType, now.minusDays(lifecycleProperties.getTrace().getArchiveAfterDays())),
                multiAgentExecutionTraceMapper.countByAgentTypeBefore(agentType, now.minusDays(lifecycleProperties.getTrace().getDeleteAfterDays()))
        );
    }

    private AgentLogLifecycleBucket buildBucket(String type,
                                                int archiveAfterDays,
                                                int deleteAfterDays,
                                                long totalCount,
                                                long archiveCandidateCount,
                                                long deleteCandidateCount) {
        long activeCount = Math.max(0L, totalCount - archiveCandidateCount);
        long archiveOnlyCount = Math.max(0L, archiveCandidateCount - deleteCandidateCount);
        return AgentLogLifecycleBucket.builder()
                .type(type)
                .archiveAfterDays(archiveAfterDays)
                .deleteAfterDays(deleteAfterDays)
                .activeCount(activeCount)
                .archiveCandidateCount(archiveOnlyCount)
                .deleteCandidateCount(deleteCandidateCount)
                .build();
    }
}

