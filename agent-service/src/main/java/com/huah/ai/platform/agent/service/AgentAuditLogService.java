package com.huah.ai.platform.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huah.ai.platform.agent.audit.AiAuditLogEntity;
import com.huah.ai.platform.agent.audit.AiAuditLogMapper;
import com.huah.ai.platform.agent.audit.AiToolAuditLogEntity;
import com.huah.ai.platform.agent.audit.AiToolAuditLogMapper;
import com.huah.ai.platform.agent.audit.TracePhaseSnapshot;
import com.huah.ai.platform.common.trace.TraceIdContext;
import com.huah.ai.platform.common.util.SnowflakeIdGenerator;
import com.huah.ai.platform.common.web.RequestOrigin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAuditLogService {

    private static final int MAX_AUDIT_TEXT_LENGTH = 500;
    private static final String PHASE_AUTH_LABEL = "Auth and context";
    private static final String PHASE_PREPARATION_LABEL = "Request preparation";
    private static final String PHASE_TOOLS_LABEL = "Tool execution";
    private static final String PHASE_GENERATION_LABEL = "Model generation";
    private static final String PHASE_PERSISTENCE_LABEL = "Persistence and audit";
    private static final String PHASE_AUTH_DESCRIPTION = "Request entry, authorization checks, and quota validation.";
    private static final String PHASE_PREPARATION_DESCRIPTION =
            "Session configuration loading, prompt assembly, and model request setup.";
    private static final String PHASE_TOOLS_DESCRIPTION =
            "Measured time spent in tool execution based on audit records.";
    private static final String PHASE_GENERATION_DESCRIPTION = "Model inference and response generation.";
    private static final String PHASE_PERSISTENCE_DESCRIPTION = "Audit log persistence and response finalization.";

    private final AiAuditLogMapper auditLogMapper;
    private final AiToolAuditLogMapper toolAuditLogMapper;
    private final ObjectMapper objectMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public Long saveAuditLog(String userId, String sessionId, String agentType,
                               String userMessage, String aiResponse, long latencyMs,
                               boolean success, String errorMessage,
                               long authLatencyMs, long preparationLatencyMs,
                               long modelLatencyMs, long persistenceLatencyMs,
                               int promptTokens, int completionTokens,
                               RequestOrigin requestOrigin) {
        Long logId = snowflakeIdGenerator.nextLongId();
        try {
            String traceId = TraceIdContext.currentTraceId();
            auditLogMapper.insert(AiAuditLogEntity.builder()
                    .id(logId)
                    .userId(userId)
                    .sessionId(sessionId)
                    .agentType(agentType)
                    .userMessage(trimForAudit(userMessage))
                    .aiResponse(trimForAudit(aiResponse))
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .latencyMs(latencyMs)
                    .success(success)
                    .errorMessage(errorMessage)
                    .clientIp(requestOrigin != null ? requestOrigin.getClientIp() : null)
                    .country(requestOrigin != null ? requestOrigin.getCountry() : null)
                    .province(requestOrigin != null ? requestOrigin.getProvince() : null)
                    .city(requestOrigin != null ? requestOrigin.getCity() : null)
                    .traceId(traceId)
                    .phaseBreakdownJson(buildPhaseBreakdownJson(
                            traceId, latencyMs, authLatencyMs, preparationLatencyMs, modelLatencyMs, persistenceLatencyMs))
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("audit log write failed: {}", e.getMessage());
        }
        return logId;
    }

    private String buildPhaseBreakdownJson(String traceId,
                                           long totalLatencyMs,
                                           long authLatencyMs,
                                           long preparationLatencyMs,
                                           long modelLatencyMs,
                                           long persistenceLatencyMs) {
        if (totalLatencyMs <= 0) {
            return null;
        }

        long toolLatencyMs = getToolLatencyByTraceId(traceId);
        long generationLatencyMs = Math.max(0, modelLatencyMs - toolLatencyMs);
        long assigned = authLatencyMs + preparationLatencyMs + toolLatencyMs + generationLatencyMs + persistenceLatencyMs;
        if (assigned < totalLatencyMs) {
            generationLatencyMs += totalLatencyMs - assigned;
        }

        List<TracePhaseSnapshot> phases = new ArrayList<>();
        phases.add(buildPhaseRecord("auth", PHASE_AUTH_LABEL, authLatencyMs, totalLatencyMs, PHASE_AUTH_DESCRIPTION));
        phases.add(buildPhaseRecord(
                "preparation", PHASE_PREPARATION_LABEL, preparationLatencyMs, totalLatencyMs, PHASE_PREPARATION_DESCRIPTION));
        phases.add(buildPhaseRecord("tools", PHASE_TOOLS_LABEL, toolLatencyMs, totalLatencyMs, PHASE_TOOLS_DESCRIPTION));
        phases.add(buildPhaseRecord(
                "generation", PHASE_GENERATION_LABEL, generationLatencyMs, totalLatencyMs, PHASE_GENERATION_DESCRIPTION));
        phases.add(buildPhaseRecord(
                "persistence", PHASE_PERSISTENCE_LABEL, persistenceLatencyMs, totalLatencyMs, PHASE_PERSISTENCE_DESCRIPTION));

        try {
            return objectMapper.writeValueAsString(phases);
        } catch (Exception e) {
            log.warn("serialize phase breakdown failed: {}", e.getMessage());
            return null;
        }
    }

    private TracePhaseSnapshot buildPhaseRecord(
            String key, String label, long latencyMs, long totalLatencyMs, String description) {
        long normalizedLatency = Math.max(latencyMs, 0);
        double share = totalLatencyMs <= 0 ? 0d : Math.round((normalizedLatency * 10000d / totalLatencyMs)) / 100d;
        return TracePhaseSnapshot.builder()
                .key(key)
                .label(label)
                .latencyMs(normalizedLatency)
                .share(share)
                .estimated(false)
                .description(description)
                .build();
    }

    private long getToolLatencyByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return 0L;
        }
        try {
            return toolAuditLogMapper.selectList(new QueryWrapper<AiToolAuditLogEntity>().eq("trace_id", traceId))
                    .stream()
                    .mapToLong(item -> Math.max(item.getLatencyMs() == null ? 0L : item.getLatencyMs(), 0L))
                    .sum();
        } catch (Exception e) {
            log.warn("load tool latency by traceId failed: {}", e.getMessage());
            return 0L;
        }
    }

    private String trimForAudit(String value) {
        if (value == null || value.length() <= MAX_AUDIT_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_AUDIT_TEXT_LENGTH);
    }
}

