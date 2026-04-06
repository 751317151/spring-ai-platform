package com.huah.ai.platform.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huah.ai.platform.agent.audit.AiAuditLogEntity;
import com.huah.ai.platform.agent.audit.AiAuditLogMapper;
import com.huah.ai.platform.agent.audit.AiToolAuditLogEntity;
import com.huah.ai.platform.agent.audit.AiToolAuditLogMapper;
import com.huah.ai.platform.agent.config.AgentLifecycleProperties;
import com.huah.ai.platform.agent.dto.AgentLogArchiveArtifact;
import com.huah.ai.platform.agent.dto.AgentArchivedTraceLookupResponse;
import com.huah.ai.platform.agent.dto.AgentLogArchiveDetailResponse;
import com.huah.ai.platform.agent.dto.AgentLogArchiveManifest;
import com.huah.ai.platform.agent.dto.AgentLogArchiveManifestInfo;
import com.huah.ai.platform.agent.dto.AgentLogArchivePreviewItem;
import com.huah.ai.platform.agent.dto.AgentLogArchivePreviewResponse;
import com.huah.ai.platform.agent.dto.AgentLogArchiveSample;
import com.huah.ai.platform.agent.dto.MultiAgentTraceResponse;
import com.huah.ai.platform.agent.dto.AgentLogLifecycleSummaryResponse;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionTrace;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionTraceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentLogArchiveService {

    private static final DateTimeFormatter FILE_TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AiAuditLogMapper aiAuditLogMapper;
    private final AiToolAuditLogMapper aiToolAuditLogMapper;
    private final MultiAgentExecutionTraceMapper multiAgentExecutionTraceMapper;
    private final AgentLifecycleProperties lifecycleProperties;
    private final ObjectMapper objectMapper;

    public AgentLogArchiveManifest createManifest(String agentType,
                                                  AgentLogLifecycleSummaryResponse lifecycleSummary,
                                                  boolean dryRun) {
        if (!lifecycleProperties.getArchive().isEnabled()) {
            return AgentLogArchiveManifest.builder()
                    .agentType(agentType)
                    .generatedAt(LocalDateTime.now().toString())
                    .dryRun(dryRun)
                    .lifecycleSummary(lifecycleSummary)
                    .artifacts(List.of())
                    .samples(List.of())
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        List<AgentLogArchiveSample> samples = buildSamples(agentType, now);
        Path archiveRootDir = resolveManifestDir();
        Path bundleDir = archiveRootDir.resolve(agentType + "-" + FILE_TS_FORMATTER.format(now));
        Path manifestPath = bundleDir.resolve("manifest.json");
        List<AgentLogArchiveArtifact> artifacts = dryRun ? List.of() : exportArtifacts(agentType, now, bundleDir);
        long exportedRecordCount = artifacts.stream().mapToLong(AgentLogArchiveArtifact::getRecordCount).sum();

        AgentLogArchiveManifest manifest = AgentLogArchiveManifest.builder()
                .agentType(agentType)
                .generatedAt(now.toString())
                .dryRun(dryRun)
                .bundleDir(bundleDir.toAbsolutePath().toString())
                .manifestPath(manifestPath.toAbsolutePath().toString())
                .exportedRecordCount(exportedRecordCount)
                .lifecycleSummary(lifecycleSummary)
                .artifacts(artifacts)
                .samples(samples)
                .build();

        try {
            Files.createDirectories(bundleDir);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write archive manifest for agent " + agentType, ex);
        }
        return manifest;
    }

    public AgentLogArchiveManifestInfo findLatestManifestInfo(String agentType) {
        Path manifestDir = resolveManifestDir();
        AgentLogArchiveManifestInfo.AgentLogArchiveManifestInfoBuilder builder = AgentLogArchiveManifestInfo.builder()
                .enabled(lifecycleProperties.getArchive().isEnabled())
                .manifestDir(manifestDir.toAbsolutePath().toString());
        if (!lifecycleProperties.getArchive().isEnabled() || !Files.exists(manifestDir)) {
            return builder.build();
        }

        try (Stream<Path> dirs = Files.list(manifestDir)) {
            Optional<Path> latest = dirs
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(agentType + "-"))
                    .max(Comparator.comparing(Path::getFileName));
            if (latest.isEmpty()) {
                return builder.build();
            }
            Path manifestPath = latest.get().resolve("manifest.json");
            if (!Files.exists(manifestPath)) {
                return builder.build();
            }
            AgentLogArchiveManifest manifest = objectMapper.readValue(manifestPath.toFile(), AgentLogArchiveManifest.class);
            return builder
                    .bundleDir(latest.get().toAbsolutePath().toString())
                    .manifestPath(manifestPath.toAbsolutePath().toString())
                    .generatedAt(manifest.getGeneratedAt())
                    .exportedRecordCount(manifest.getExportedRecordCount())
                    .build();
        } catch (IOException ex) {
            log.warn("Failed to read latest archive manifest for agent={}", agentType, ex);
            return builder.build();
        }
    }

    public AgentLogArchiveDetailResponse loadLatestManifest(String agentType) {
        Path manifestDir = resolveManifestDir();
        if (!lifecycleProperties.getArchive().isEnabled() || !Files.exists(manifestDir)) {
            return AgentLogArchiveDetailResponse.builder()
                    .agentType(agentType)
                    .enabled(lifecycleProperties.getArchive().isEnabled())
                    .manifestDir(manifestDir.toAbsolutePath().toString())
                    .coldDataCount(0L)
                    .sampleLimit(lifecycleProperties.getArchive().getSampleLimit())
                    .exportBatchSize(lifecycleProperties.getArchive().getExportBatchSize())
                    .operationHints(buildOperationHints(null))
                    .artifacts(List.of())
                    .samples(List.of())
                    .build();
        }
        try (Stream<Path> dirs = Files.list(manifestDir)) {
            Optional<Path> latest = dirs
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(agentType + "-"))
                    .max(Comparator.comparing(Path::getFileName));
            if (latest.isEmpty()) {
                return AgentLogArchiveDetailResponse.builder()
                        .agentType(agentType)
                        .enabled(true)
                        .manifestDir(manifestDir.toAbsolutePath().toString())
                        .coldDataCount(0L)
                        .sampleLimit(lifecycleProperties.getArchive().getSampleLimit())
                        .exportBatchSize(lifecycleProperties.getArchive().getExportBatchSize())
                        .operationHints(buildOperationHints(null))
                        .artifacts(List.of())
                        .samples(List.of())
                        .build();
            }
            Path manifestPath = latest.get().resolve("manifest.json");
            if (!Files.exists(manifestPath)) {
                return AgentLogArchiveDetailResponse.builder()
                        .agentType(agentType)
                        .enabled(true)
                        .manifestDir(manifestDir.toAbsolutePath().toString())
                        .bundleDir(latest.get().toAbsolutePath().toString())
                        .coldDataCount(0L)
                        .sampleLimit(lifecycleProperties.getArchive().getSampleLimit())
                        .exportBatchSize(lifecycleProperties.getArchive().getExportBatchSize())
                        .operationHints(buildOperationHints(latest.get().toAbsolutePath().toString()))
                        .artifacts(List.of())
                        .samples(List.of())
                        .build();
            }
            AgentLogArchiveManifest manifest = objectMapper.readValue(manifestPath.toFile(), AgentLogArchiveManifest.class);
            return AgentLogArchiveDetailResponse.builder()
                    .agentType(agentType)
                    .enabled(true)
                    .manifestDir(manifestDir.toAbsolutePath().toString())
                    .bundleDir(manifest.getBundleDir())
                    .manifestPath(manifest.getManifestPath())
                    .generatedAt(manifest.getGeneratedAt())
                    .dryRun(manifest.isDryRun())
                    .exportedRecordCount(manifest.getExportedRecordCount())
                    .coldDataCount(manifest.getExportedRecordCount())
                    .sampleLimit(lifecycleProperties.getArchive().getSampleLimit())
                    .exportBatchSize(lifecycleProperties.getArchive().getExportBatchSize())
                    .operationHints(buildOperationHints(manifest.getBundleDir()))
                    .artifacts(manifest.getArtifacts() == null ? List.of() : manifest.getArtifacts())
                    .samples(manifest.getSamples() == null ? List.of() : manifest.getSamples())
                    .build();
        } catch (IOException ex) {
            log.warn("Failed to load latest archive manifest detail for agent={}", agentType, ex);
            return AgentLogArchiveDetailResponse.builder()
                    .agentType(agentType)
                    .enabled(lifecycleProperties.getArchive().isEnabled())
                    .manifestDir(manifestDir.toAbsolutePath().toString())
                    .coldDataCount(0L)
                    .sampleLimit(lifecycleProperties.getArchive().getSampleLimit())
                    .exportBatchSize(lifecycleProperties.getArchive().getExportBatchSize())
                    .operationHints(buildOperationHints(null))
                    .artifacts(List.of())
                    .samples(List.of())
                    .build();
        }
    }

    public MultiAgentExecutionTrace loadArchivedTraceRecord(String agentType, String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        AgentLogArchiveDetailResponse detail = loadLatestManifest(agentType);
        if (detail.getArtifacts() == null) {
            return null;
        }
        AgentLogArchiveArtifact artifact = detail.getArtifacts().stream()
                .filter(item -> "trace".equalsIgnoreCase(item.getType()))
                .findFirst()
                .orElse(null);
        if (artifact == null || artifact.getPath() == null) {
            return null;
        }
        Path artifactPath = Path.of(artifact.getPath());
        if (!Files.exists(artifactPath)) {
            return null;
        }
        try (Stream<String> lines = Files.lines(artifactPath, StandardCharsets.UTF_8)) {
            return lines
                    .map(this::readTraceSafely)
                    .filter(item -> item != null && traceId.equals(item.getTraceId()))
                    .findFirst()
                    .orElse(null);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load archived trace " + traceId, ex);
        }
    }

    public AgentArchivedTraceLookupResponse findArchivedTrace(String agentType, String traceId) {
        AgentLogArchiveDetailResponse detail = loadLatestManifest(agentType);
        if (traceId == null || traceId.isBlank() || detail.getArtifacts() == null) {
            return AgentArchivedTraceLookupResponse.builder()
                    .agentType(agentType)
                    .found(false)
                    .traceId(traceId)
                    .build();
        }
        AgentLogArchiveArtifact artifact = detail.getArtifacts().stream()
                .filter(item -> "trace".equalsIgnoreCase(item.getType()))
                .findFirst()
                .orElse(null);
        if (artifact == null || artifact.getPath() == null) {
            return AgentArchivedTraceLookupResponse.builder()
                    .agentType(agentType)
                    .found(false)
                    .traceId(traceId)
                    .build();
        }
        Path artifactPath = Path.of(artifact.getPath());
        if (!Files.exists(artifactPath)) {
            return AgentArchivedTraceLookupResponse.builder()
                    .agentType(agentType)
                    .found(false)
                    .traceId(traceId)
                    .artifactType("trace")
                    .artifactPath(artifact.getPath())
                    .build();
        }
        try (Stream<String> lines = Files.lines(artifactPath, StandardCharsets.UTF_8)) {
            Optional<MultiAgentExecutionTrace> matched = lines
                    .map(line -> readTraceSafely(line))
                    .filter(item -> item != null && traceId.equals(item.getTraceId()))
                    .findFirst();
            if (matched.isEmpty()) {
                return AgentArchivedTraceLookupResponse.builder()
                        .agentType(agentType)
                        .found(false)
                        .traceId(traceId)
                        .artifactType("trace")
                        .artifactPath(artifact.getPath())
                        .build();
            }
            MultiAgentExecutionTrace trace = matched.get();
            return AgentArchivedTraceLookupResponse.builder()
                    .agentType(agentType)
                    .found(true)
                    .artifactType("trace")
                    .artifactPath(artifact.getPath())
                    .traceId(traceId)
                    .archivedAt(detail.getGeneratedAt())
                    .summary(firstNonBlank(trace.getFinalSummary(), trace.getRequestSummary(), trace.getErrorMessage()))
                    .replayable(trace.getRequestSummary() != null && !trace.getRequestSummary().isBlank())
                    .trace(toArchivedTraceResponse(trace))
                    .build();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to search archived trace " + traceId, ex);
        }
    }

    public AgentLogArchivePreviewResponse previewLatestArtifact(String agentType, String artifactType, int limit) {
        AgentLogArchiveDetailResponse detail = loadLatestManifest(agentType);
        int safeLimit = Math.max(1, Math.min(limit, 20));
        if (detail.getArtifacts() == null || detail.getArtifacts().isEmpty()) {
            return AgentLogArchivePreviewResponse.builder()
                    .agentType(agentType)
                    .artifactType(artifactType)
                    .bundleDir(detail.getBundleDir())
                    .previewLimit(safeLimit)
                    .items(List.of())
                    .build();
        }
        AgentLogArchiveArtifact artifact = detail.getArtifacts().stream()
                .filter(item -> item.getType() != null && item.getType().equalsIgnoreCase(artifactType))
                .findFirst()
                .orElse(null);
        if (artifact == null || artifact.getPath() == null || artifact.getPath().isBlank()) {
            return AgentLogArchivePreviewResponse.builder()
                    .agentType(agentType)
                    .artifactType(artifactType)
                    .bundleDir(detail.getBundleDir())
                    .previewLimit(safeLimit)
                    .items(List.of())
                    .build();
        }

        Path artifactPath = Path.of(artifact.getPath());
        if (!Files.exists(artifactPath)) {
            return AgentLogArchivePreviewResponse.builder()
                    .agentType(agentType)
                    .artifactType(artifactType)
                    .bundleDir(detail.getBundleDir())
                    .artifactPath(artifact.getPath())
                    .previewLimit(safeLimit)
                    .items(List.of())
                    .build();
        }
        try (Stream<String> lines = Files.lines(artifactPath, StandardCharsets.UTF_8)) {
            List<String> previewLines = lines.limit(safeLimit).toList();
            List<AgentLogArchivePreviewItem> items = IntStream.range(0, previewLines.size())
                    .mapToObj(index -> AgentLogArchivePreviewItem.builder()
                            .lineNumber(index + 1)
                            .content(previewLines.get(index))
                            .build())
                    .toList();
            return AgentLogArchivePreviewResponse.builder()
                    .agentType(agentType)
                    .artifactType(artifactType)
                    .bundleDir(detail.getBundleDir())
                    .artifactPath(artifact.getPath())
                    .previewLimit(safeLimit)
                    .items(items)
                    .build();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to preview archive artifact " + artifactType, ex);
        }
    }

    private List<AgentLogArchiveArtifact> exportArtifacts(String agentType, LocalDateTime now, Path bundleDir) {
        int batchSize = Math.max(1, lifecycleProperties.getArchive().getExportBatchSize());
        LocalDateTime auditBefore = now.minusDays(lifecycleProperties.getAudit().getArchiveAfterDays());
        LocalDateTime toolAuditBefore = now.minusDays(lifecycleProperties.getToolAudit().getArchiveAfterDays());
        LocalDateTime traceBefore = now.minusDays(lifecycleProperties.getTrace().getArchiveAfterDays());

        try {
            Files.createDirectories(bundleDir);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create archive bundle dir for agent " + agentType, ex);
        }

        List<AgentLogArchiveArtifact> artifacts = new ArrayList<>();
        artifacts.add(exportJsonlArtifact(
                "audit",
                bundleDir.resolve("audit.jsonl"),
                (limit, offset) -> aiAuditLogMapper.selectArchiveCandidatesBatch(agentType, auditBefore, limit, offset),
                batchSize
        ));
        artifacts.add(exportJsonlArtifact(
                "tool-audit",
                bundleDir.resolve("tool-audit.jsonl"),
                (limit, offset) -> aiToolAuditLogMapper.selectArchiveCandidatesBatch(agentType, toolAuditBefore, limit, offset),
                batchSize
        ));
        artifacts.add(exportJsonlArtifact(
                "trace",
                bundleDir.resolve("trace.jsonl"),
                (limit, offset) -> multiAgentExecutionTraceMapper.selectArchiveCandidatesBatch(agentType, traceBefore, limit, offset),
                batchSize
        ));
        return artifacts;
    }

    private <T> AgentLogArchiveArtifact exportJsonlArtifact(String type,
                                                            Path outputPath,
                                                            BiFunction<Integer, Long, List<T>> fetcher,
                                                            int batchSize) {
        long offset = 0L;
        long recordCount = 0L;
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            while (true) {
                List<T> batch = fetcher.apply(batchSize, offset);
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                for (T item : batch) {
                    writer.write(objectMapper.writeValueAsString(item));
                    writer.newLine();
                    recordCount++;
                }
                if (batch.size() < batchSize) {
                    break;
                }
                offset += batch.size();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to export archive artifact " + type, ex);
        }
        return AgentLogArchiveArtifact.builder()
                .type(type)
                .path(outputPath.toAbsolutePath().toString())
                .recordCount(recordCount)
                .build();
    }

    private List<AgentLogArchiveSample> buildSamples(String agentType, LocalDateTime now) {
        int limit = Math.max(1, lifecycleProperties.getArchive().getSampleLimit());
        List<AgentLogArchiveSample> samples = new ArrayList<>();
        LocalDateTime auditBefore = now.minusDays(lifecycleProperties.getAudit().getArchiveAfterDays());
        LocalDateTime toolAuditBefore = now.minusDays(lifecycleProperties.getToolAudit().getArchiveAfterDays());
        LocalDateTime traceBefore = now.minusDays(lifecycleProperties.getTrace().getArchiveAfterDays());

        aiAuditLogMapper.selectArchiveCandidates(agentType, auditBefore, limit)
                .stream()
                .map(this::toAuditSample)
                .forEach(samples::add);
        aiToolAuditLogMapper.selectArchiveCandidates(agentType, toolAuditBefore, limit)
                .stream()
                .map(this::toToolAuditSample)
                .forEach(samples::add);
        multiAgentExecutionTraceMapper.selectArchiveCandidates(agentType, traceBefore, limit)
                .stream()
                .map(this::toTraceSample)
                .forEach(samples::add);
        return samples;
    }

    private AgentLogArchiveSample toAuditSample(AiAuditLogEntity log) {
        return AgentLogArchiveSample.builder()
                .type("audit")
                .id(log.getId() == null ? null : String.valueOf(log.getId()))
                .traceId(log.getTraceId())
                .sessionId(log.getSessionId())
                .summary(firstNonBlank(log.getErrorMessage(), log.getUserMessage(), log.getAiResponse()))
                .createdAt(log.getCreatedAt() == null ? null : log.getCreatedAt().toString())
                .build();
    }

    private AgentLogArchiveSample toToolAuditSample(AiToolAuditLogEntity log) {
        return AgentLogArchiveSample.builder()
                .type("tool-audit")
                .id(log.getId() == null ? null : String.valueOf(log.getId()))
                .traceId(log.getTraceId())
                .sessionId(log.getSessionId())
                .summary(firstNonBlank(log.getErrorMessage(), log.getInputSummary(), log.getToolName()))
                .createdAt(log.getCreatedAt() == null ? null : log.getCreatedAt().toString())
                .build();
    }

    private AgentLogArchiveSample toTraceSample(MultiAgentExecutionTrace trace) {
        return AgentLogArchiveSample.builder()
                .type("trace")
                .id(trace.getId() == null ? null : String.valueOf(trace.getId()))
                .traceId(trace.getTraceId())
                .sessionId(trace.getSessionId())
                .summary(firstNonBlank(trace.getErrorMessage(), trace.getRequestSummary(), trace.getFinalSummary()))
                .createdAt(trace.getCreatedAt() == null ? null : trace.getCreatedAt().toString())
                .build();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private List<String> buildOperationHints(String bundleDir) {
        List<String> hints = new ArrayList<>();
        hints.add("Use archived trace lookup to search cold trace data by traceId before live recovery.");
        hints.add("Use archive replay to rerun a cold multi-agent request as a new trace when step records are unavailable.");
        if (bundleDir != null && !bundleDir.isBlank()) {
            hints.add("Archive bundle is stored at " + bundleDir + " and contains audit.jsonl, tool-audit.jsonl and trace.jsonl.");
        }
        return hints;
    }

    private MultiAgentExecutionTrace readTraceSafely(String line) {
        try {
            return objectMapper.readValue(line, MultiAgentExecutionTrace.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private MultiAgentTraceResponse toArchivedTraceResponse(MultiAgentExecutionTrace trace) {
        return MultiAgentTraceResponse.builder()
                .traceId(trace.getTraceId())
                .sessionId(trace.getSessionId())
                .userId(trace.getUserId())
                .agentType(trace.getAgentType())
                .requestSummary(trace.getRequestSummary())
                .finalSummary(trace.getFinalSummary())
                .status(trace.getStatus())
                .totalPromptTokens(trace.getTotalPromptTokens())
                .totalCompletionTokens(trace.getTotalCompletionTokens())
                .totalLatencyMs(trace.getTotalLatencyMs())
                .stepCount(trace.getStepCount())
                .errorMessage(trace.getErrorMessage())
                .parentTraceId(trace.getParentTraceId())
                .recoverySourceTraceId(trace.getRecoverySourceTraceId())
                .recoverySourceStepOrder(trace.getRecoverySourceStepOrder())
                .recoveryAction(trace.getRecoveryAction())
                .createdAt(trace.getCreatedAt())
                .updatedAt(trace.getUpdatedAt())
                .steps(List.of())
                .build();
    }

    private Path resolveManifestDir() {
        return Paths.get(lifecycleProperties.getArchive().getManifestDir());
    }
}

