package com.huah.ai.platform.monitor.service;

import com.huah.ai.platform.monitor.alert.AlertEvaluationService;
import com.huah.ai.platform.monitor.alert.AlertEventView;
import com.huah.ai.platform.monitor.alert.AlertMetricsSnapshot;
import com.huah.ai.platform.monitor.alert.AlertmanagerAlertService;
import com.huah.ai.platform.monitor.model.AgentStatView;
import com.huah.ai.platform.monitor.model.AlertsView;
import com.huah.ai.platform.monitor.model.AuditLogView;
import com.huah.ai.platform.monitor.model.AlertWorkflowHistoryView;
import com.huah.ai.platform.monitor.model.FailureSampleView;
import com.huah.ai.platform.monitor.model.EvidenceFeedbackSampleView;
import com.huah.ai.platform.monitor.model.FeedbackOverviewView;
import com.huah.ai.platform.monitor.model.FeedbackSampleView;
import com.huah.ai.platform.monitor.model.HourlyStatView;
import com.huah.ai.platform.monitor.model.ModelStatView;
import com.huah.ai.platform.monitor.model.MonitorOverviewView;
import com.huah.ai.platform.monitor.model.SlowRequestView;
import com.huah.ai.platform.monitor.model.TraceDetailView;
import com.huah.ai.platform.monitor.model.TokenUsageView;
import com.huah.ai.platform.monitor.model.TopUserView;
import com.huah.ai.platform.monitor.model.ToolAuditView;
import com.huah.ai.platform.monitor.model.TracePhaseView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorQueryService {

    private static final DateTimeFormatter CSV_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String PHASE_AUTH_LABEL = "Auth and context";
    private static final String PHASE_RETRIEVAL_LABEL = "Retrieval and preparation";
    private static final String PHASE_TOOLS_LABEL = "Tool execution";
    private static final String PHASE_GENERATION_LABEL = "Model generation";
    private static final String PHASE_PERSISTENCE_LABEL = "Persistence and audit";
    private static final String PHASE_AUTH_DESCRIPTION = "Gateway entry, authorization checks, and request context setup.";
    private static final String PHASE_RETRIEVAL_DESCRIPTION = "Knowledge retrieval, prompt preparation, and model input assembly.";
    private static final String PHASE_TOOLS_EMPTY_DESCRIPTION = "No tool execution was recorded for this trace.";
    private static final String PHASE_TOOLS_DESCRIPTION = "Accumulated tool latency derived from audit records.";
    private static final String PHASE_GENERATION_DESCRIPTION = "Model inference and final answer generation.";
    private static final String PHASE_PERSISTENCE_DESCRIPTION = "Audit logging, feedback linkage, and persistence work.";

    private final MeterRegistry meterRegistry;
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final AlertEvaluationService alertEvaluationService;
    private final AlertmanagerAlertService alertmanagerAlertService;
    private final AlertWorkflowService alertWorkflowService;
    private final ObjectMapper objectMapper;

    public MonitorOverviewView getOverview() {
        try {
            String today = LocalDate.now().toString();
            var todayStats = jdbcTemplate.queryForMap(
                    "SELECT COUNT(*) as total, " +
                            "COUNT(*) FILTER (WHERE success = false) as errors, " +
                            "COALESCE(AVG(latency_ms), 0) as avg_latency, " +
                            "COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms), 0) as p95_latency, " +
                            "COALESCE(PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY latency_ms), 0) as p99_latency, " +
                            "COALESCE(SUM(COALESCE(prompt_tokens, 0)), 0) as prompt_tokens, " +
                            "COALESCE(SUM(COALESCE(completion_tokens, 0)), 0) as completion_tokens " +
                            "FROM ai_audit_logs WHERE created_at >= ?::date",
                    today
            );

            long total = ((Number) todayStats.get("total")).longValue();
            long errors = ((Number) todayStats.get("errors")).longValue();
            long promptTokens = ((Number) todayStats.get("prompt_tokens")).longValue();
            long completionTokens = ((Number) todayStats.get("completion_tokens")).longValue();

            return MonitorOverviewView.builder()
                    .totalRequests(total)
                    .errorRequests(errors)
                    .successRate(total > 0 ? (double) (total - errors) / total : 1.0)
                    .avgLatencyMs(((Number) todayStats.get("avg_latency")).longValue())
                    .p95LatencyMs(((Number) todayStats.get("p95_latency")).longValue())
                    .p99LatencyMs(((Number) todayStats.get("p99_latency")).longValue())
                    .totalPromptTokens(promptTokens)
                    .totalCompletionTokens(completionTokens)
                    .totalTokens(promptTokens + completionTokens)
                    .activeRequests(getGaugeValue("ai.requests.active"))
                    .build();
        } catch (Exception e) {
            log.warn("Failed to load monitor overview: {}", e.getMessage());
            return MonitorOverviewView.builder()
                    .totalRequests(0)
                    .errorRequests(0)
                    .successRate(1.0)
                    .avgLatencyMs(0)
                    .p95LatencyMs(0)
                    .p99LatencyMs(0)
                    .totalPromptTokens(0)
                    .totalCompletionTokens(0)
                    .totalTokens(0)
                    .activeRequests(0)
                    .build();
        }
    }

    public List<AgentStatView> getAgentStats() {
        try {
            String today = LocalDate.now().toString();
            return jdbcTemplate.query(
                    "SELECT agent_type, COUNT(*) as count, " +
                            "COALESCE(AVG(latency_ms), 0) as avg_latency, " +
                            "COUNT(*) FILTER (WHERE success = false) as errors " +
                            "FROM ai_audit_logs WHERE created_at >= ?::date " +
                            "GROUP BY agent_type ORDER BY count DESC",
                    (rs, rowNum) -> AgentStatView.builder()
                            .agentType(rs.getString("agent_type"))
                            .count(rs.getLong("count"))
                            .avgLatency(rs.getLong("avg_latency"))
                            .errors(rs.getLong("errors"))
                            .build(),
                    today
            );
        } catch (Exception e) {
            log.warn("Failed to load agent stats: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<ModelStatView> getModelStats() {
        try {
            String today = LocalDate.now().toString();
            return jdbcTemplate.query(
                    "SELECT COALESCE(model_id, 'unknown') as model_id, COUNT(*) as count, " +
                            "COALESCE(AVG(latency_ms), 0) as avg_latency, " +
                            "COUNT(*) FILTER (WHERE success = false) as errors " +
                            "FROM ai_audit_logs WHERE created_at >= ?::date " +
                            "GROUP BY COALESCE(model_id, 'unknown') ORDER BY count DESC",
                    (rs, rowNum) -> ModelStatView.builder()
                            .modelId(rs.getString("model_id"))
                            .count(rs.getLong("count"))
                            .avgLatency(rs.getLong("avg_latency"))
                            .errors(rs.getLong("errors"))
                            .build(),
                    today
            );
        } catch (Exception e) {
            log.warn("Failed to load model stats: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public TokenUsageView getTokenUsage(String userId) {
        String today = LocalDate.now().toString();
        String key = "ai:token:daily:" + userId + ":" + today;
        String usage = redisTemplate.opsForValue().get(key);
        return TokenUsageView.builder()
                .userId(userId)
                .date(today)
                .tokensUsed(usage != null ? Long.parseLong(usage) : 0L)
                .build();
    }

    public List<AuditLogView> getAuditLogs(int limit, String userId) {
        try {
            String sql = userId != null
                    ? "SELECT id, user_id, agent_type, model_id, error_message, session_id, trace_id, latency_ms, success, created_at " +
                    "FROM ai_audit_logs WHERE user_id = ? ORDER BY created_at DESC LIMIT ?"
                    : "SELECT id, user_id, agent_type, model_id, error_message, session_id, trace_id, latency_ms, success, created_at " +
                    "FROM ai_audit_logs ORDER BY created_at DESC LIMIT ?";
            return userId != null
                    ? jdbcTemplate.query(sql, this::mapAuditLog, userId, limit)
                    : jdbcTemplate.query(sql, this::mapAuditLog, limit);
        } catch (Exception e) {
            log.warn("Failed to load audit logs: userId={}, limit={}, error={}", userId, limit, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<TopUserView> getTopUsers() {
        try {
            String sql = "SELECT user_id, agent_type, COUNT(*) as calls, " +
                    "AVG(latency_ms) as avg_latency " +
                    "FROM ai_audit_logs WHERE created_at > ? " +
                    "GROUP BY user_id, agent_type ORDER BY calls DESC LIMIT 10";
            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> TopUserView.builder()
                            .userId(rs.getString("user_id"))
                            .agentType(rs.getString("agent_type"))
                            .calls(rs.getLong("calls"))
                            .avgLatency(rs.getLong("avg_latency"))
                            .build(),
                    LocalDateTime.now().minusDays(1));
        } catch (Exception e) {
            log.warn("Failed to load top users: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<SlowRequestView> getSlowRequests(int limit) {
        try {
            return jdbcTemplate.query(
                    "SELECT id, user_id, agent_type, model_id, trace_id, latency_ms, success, created_at " +
                            "FROM ai_audit_logs ORDER BY latency_ms DESC, created_at DESC LIMIT ?",
                    (rs, rowNum) -> SlowRequestView.builder()
                            .id(rs.getString("id"))
                            .userId(rs.getString("user_id"))
                            .agentType(rs.getString("agent_type"))
                            .modelId(rs.getString("model_id"))
                            .traceId(rs.getString("trace_id"))
                            .latencyMs(rs.getLong("latency_ms"))
                            .success(rs.getBoolean("success"))
                            .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                            .build(),
                    limit
            );
        } catch (Exception e) {
            log.warn("Failed to load slow requests: limit={}, error={}", limit, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<FailureSampleView> getFailureSamples(int limit) {
        try {
            return jdbcTemplate.query(
                    "SELECT id, user_id, agent_type, model_id, error_message, latency_ms, session_id, trace_id, created_at " +
                            "FROM ai_audit_logs WHERE success = false ORDER BY created_at DESC LIMIT ?",
                    (rs, rowNum) -> FailureSampleView.builder()
                            .id(rs.getString("id"))
                            .userId(rs.getString("user_id"))
                            .agentType(rs.getString("agent_type"))
                            .modelId(rs.getString("model_id"))
                            .errorMessage(rs.getString("error_message"))
                            .latencyMs(rs.getLong("latency_ms"))
                            .sessionId(rs.getString("session_id"))
                            .traceId(rs.getString("trace_id"))
                            .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                            .build(),
                    limit
            );
        } catch (Exception e) {
            log.warn("Failed to load failure samples: limit={}, error={}", limit, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<ToolAuditView> getToolAudits(int limit, String userId, String agentType, String toolName) {
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT id, user_id, session_id, agent_type, tool_name, tool_class, input_summary, output_summary, " +
                            "success, error_message, latency_ms, trace_id, created_at FROM ai_tool_audit_logs WHERE 1 = 1"
            );
            List<Object> args = new java.util.ArrayList<>();
            if (userId != null && !userId.isBlank()) {
                sql.append(" AND user_id = ?");
                args.add(userId);
            }
            if (agentType != null && !agentType.isBlank()) {
                sql.append(" AND agent_type = ?");
                args.add(agentType);
            }
            if (toolName != null && !toolName.isBlank()) {
                sql.append(" AND tool_name = ?");
                args.add(toolName);
            }
            sql.append(" ORDER BY created_at DESC LIMIT ?");
            args.add(limit);
            return jdbcTemplate.query(sql.toString(), this::mapToolAudit, args.toArray());
        } catch (Exception e) {
            log.warn("Failed to load tool audits: userId={}, agentType={}, toolName={}, limit={}, error={}",
                    userId, agentType, toolName, limit, e.getMessage());
            return Collections.emptyList();
        }
    }

    public Optional<TraceDetailView> getTraceDetail(String traceId) {
        try {
            List<TraceDetailView> rows = jdbcTemplate.query(
                    "SELECT id, trace_id, user_id, agent_type, model_id, session_id, success, error_message, " +
                            "latency_ms, prompt_tokens, completion_tokens, created_at, user_message, ai_response, phase_breakdown_json " +
                            "FROM ai_audit_logs WHERE trace_id = ? ORDER BY created_at DESC LIMIT 1",
                    (rs, rowNum) -> {
                        List<ToolAuditView> toolExecutions = getToolAuditsByTraceId(traceId);
                        long latencyMs = rs.getLong("latency_ms");
                        String phaseBreakdownJson = rs.getString("phase_breakdown_json");
                        return TraceDetailView.builder()
                            .id(rs.getString("id"))
                            .traceId(rs.getString("trace_id"))
                            .userId(rs.getString("user_id"))
                            .agentType(rs.getString("agent_type"))
                            .modelId(rs.getString("model_id"))
                            .sessionId(rs.getString("session_id"))
                            .success(rs.getBoolean("success"))
                            .errorMessage(rs.getString("error_message"))
                            .latencyMs(latencyMs)
                            .promptTokens((Integer) rs.getObject("prompt_tokens"))
                            .completionTokens((Integer) rs.getObject("completion_tokens"))
                            .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                            .userMessage(rs.getString("user_message"))
                            .aiResponse(rs.getString("ai_response"))
                            .toolExecutions(toolExecutions)
                            .phaseBreakdown(resolveTracePhases(phaseBreakdownJson, latencyMs, toolExecutions, rs.getBoolean("success")))
                            .build();
                    },
                    traceId
            );
            return rows.stream().findFirst();
        } catch (Exception e) {
            log.warn("Failed to load trace detail: traceId={}, error={}", traceId, e.getMessage());
            return Optional.empty();
        }
    }

    public FeedbackOverviewView getFeedbackOverview() {
        try {
            String today = LocalDate.now().toString();
            var stats = jdbcTemplate.queryForMap(
                    "SELECT COUNT(*) AS total, " +
                            "COUNT(*) FILTER (WHERE feedback = 'up') AS positive, " +
                            "COUNT(*) FILTER (WHERE feedback = 'down') AS negative " +
                            "FROM ai_response_feedback WHERE created_at >= ?::date",
                    today
            );
            long total = ((Number) stats.get("total")).longValue();
            long positive = ((Number) stats.get("positive")).longValue();
            long negative = ((Number) stats.get("negative")).longValue();
            return FeedbackOverviewView.builder()
                    .totalCount(total)
                    .positiveCount(positive)
                    .negativeCount(negative)
                    .positiveRate(total > 0 ? (double) positive / total : 0)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to load feedback overview: {}", e.getMessage());
            return FeedbackOverviewView.builder()
                    .totalCount(0)
                    .positiveCount(0)
                    .negativeCount(0)
                    .positiveRate(0)
                    .build();
        }
    }

    public List<FeedbackSampleView> getRecentFeedback(int limit) {
        try {
            return jdbcTemplate.query(
                    "SELECT f.response_id, a.user_id, f.source_type, a.agent_type, f.knowledge_base_id, f.feedback, f.comment, f.created_at " +
                            "FROM ai_response_feedback f " +
                            "LEFT JOIN ai_audit_logs a ON a.id = f.response_id " +
                            "ORDER BY f.created_at DESC LIMIT ?",
                    (rs, rowNum) -> FeedbackSampleView.builder()
                            .responseId(rs.getString("response_id"))
                            .userId(rs.getString("user_id"))
                            .sourceType(rs.getString("source_type"))
                            .agentType(rs.getString("agent_type"))
                            .knowledgeBaseId(rs.getString("knowledge_base_id"))
                            .feedback(rs.getString("feedback"))
                            .comment(rs.getString("comment"))
                            .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                            .build(),
                    limit
            );
        } catch (Exception e) {
            log.warn("Failed to load recent feedback: limit={}, error={}", limit, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<EvidenceFeedbackSampleView> getRecentEvidenceFeedback(int limit) {
        try {
            return jdbcTemplate.query(
                    "SELECT e.response_id, e.chunk_id, a.user_id, e.knowledge_base_id, e.feedback, e.comment, e.created_at " +
                            "FROM ai_evidence_feedback e " +
                            "LEFT JOIN ai_audit_logs a ON a.id = e.response_id " +
                            "ORDER BY e.created_at DESC LIMIT ?",
                    (rs, rowNum) -> EvidenceFeedbackSampleView.builder()
                            .responseId(rs.getString("response_id"))
                            .chunkId(rs.getString("chunk_id"))
                            .userId(rs.getString("user_id"))
                            .knowledgeBaseId(rs.getString("knowledge_base_id"))
                            .feedback(rs.getString("feedback"))
                            .comment(rs.getString("comment"))
                            .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                            .build(),
                    limit
            );
        } catch (Exception e) {
            log.warn("Failed to load recent evidence feedback: limit={}, error={}", limit, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<HourlyStatView> getHourlyStats() {
        try {
            String today = LocalDate.now().toString();
            return jdbcTemplate.query(
                    "SELECT EXTRACT(HOUR FROM created_at)::int as hour, " +
                            "COUNT(*) as total, " +
                            "COUNT(*) FILTER (WHERE success = false) as errors, " +
                            "COALESCE(AVG(latency_ms), 0) as avg_latency, " +
                            "COALESCE(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY latency_ms), 0) as p50, " +
                            "COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms), 0) as p95 " +
                            "FROM ai_audit_logs WHERE created_at >= ?::date " +
                            "GROUP BY EXTRACT(HOUR FROM created_at) ORDER BY hour",
                    (rs, rowNum) -> HourlyStatView.builder()
                            .hour(rs.getInt("hour"))
                            .total(rs.getLong("total"))
                            .errors(rs.getLong("errors"))
                            .avg_latency(rs.getDouble("avg_latency"))
                            .p50(rs.getDouble("p50"))
                            .p95(rs.getDouble("p95"))
                            .build(),
                    today
            );
        } catch (Exception e) {
            log.warn("Failed to load hourly stats: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public AlertsView getAlerts() {
        List<AlertEventView> alerts = alertmanagerAlertService.fetchActiveAlerts();
        if (alerts.isEmpty()) {
            alerts = alertEvaluationService.evaluate(buildAlertSnapshot());
        }
        Map<String, AlertWorkflowService.AlertWorkflowRecord> workflowMap = alertWorkflowService.getWorkflowMap(
                alerts.stream()
                        .map(AlertEventView::getFingerprint)
                        .filter(value -> value != null && !value.isBlank())
                        .toList()
        );
        alerts = alerts.stream()
                .map(item -> {
                    AlertWorkflowService.AlertWorkflowRecord workflow = workflowMap.get(item.getFingerprint());
                    if (workflow == null) {
                        return item;
                    }
                    return AlertEventView.builder()
                            .level(item.getLevel())
                            .type(item.getType())
                            .message(item.getMessage())
                            .time(item.getTime())
                            .source(item.getSource())
                            .status(item.getStatus())
                            .fingerprint(item.getFingerprint())
                            .silenceUrl(item.getSilenceUrl())
                            .workflowStatus(workflow.getWorkflowStatus())
                            .workflowNote(workflow.getWorkflowNote())
                            .workflowUpdatedAt(workflow.getUpdatedAt())
                            .silencedUntil(workflow.getSilencedUntil())
                            .labels(item.getLabels())
                            .build();
                })
                .toList();
        return AlertsView.builder()
                .activeAlerts(alerts.stream().filter(a -> !"INFO".equals(a.getLevel())).count())
                .alerts(alerts)
                .build();
    }

    public List<AlertWorkflowHistoryView> getAlertWorkflowHistory(String fingerprint, int limit) {
        return alertWorkflowService.getWorkflowHistory(fingerprint, limit).stream()
                .map(item -> AlertWorkflowHistoryView.builder()
                        .fingerprint(item.getFingerprint())
                        .workflowStatus(item.getWorkflowStatus())
                        .workflowNote(item.getWorkflowNote())
                        .silencedUntil(item.getSilencedUntil())
                        .createdAt(item.getCreatedAt())
                        .build())
                .toList();
    }

    public void updateAlertWorkflow(String fingerprint, String workflowStatus, String workflowNote, String silencedUntil) {
        alertWorkflowService.saveWorkflow(fingerprint, workflowStatus, workflowNote, parseDateTime(silencedUntil));
    }

    public String exportSlowRequestsCsv(int limit) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("id,userId,agentType,modelId,traceId,latencyMs,success,createdAt");
        for (SlowRequestView item : getSlowRequests(limit)) {
            joiner.add(String.join(",",
                    csv(item.getId()),
                    csv(item.getUserId()),
                    csv(item.getAgentType()),
                    csv(item.getModelId()),
                    csv(item.getTraceId()),
                    String.valueOf(item.getLatencyMs()),
                    String.valueOf(item.isSuccess()),
                    csv(formatDateTime(item.getCreatedAt()))));
        }
        return joiner.toString();
    }

    public String exportFailureSamplesCsv(int limit) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("id,userId,agentType,modelId,errorMessage,latencyMs,sessionId,traceId,createdAt");
        for (FailureSampleView item : getFailureSamples(limit)) {
            joiner.add(String.join(",",
                    csv(item.getId()),
                    csv(item.getUserId()),
                    csv(item.getAgentType()),
                    csv(item.getModelId()),
                    csv(item.getErrorMessage()),
                    String.valueOf(item.getLatencyMs()),
                    csv(item.getSessionId()),
                    csv(item.getTraceId()),
                    csv(formatDateTime(item.getCreatedAt()))));
        }
        return joiner.toString();
    }

    public String exportFeedbackCsv(int limit) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("responseId,userId,sourceType,agentType,knowledgeBaseId,feedback,comment,createdAt");
        for (FeedbackSampleView item : getRecentFeedback(limit)) {
            joiner.add(String.join(",",
                    csv(item.getResponseId()),
                    csv(item.getUserId()),
                    csv(item.getSourceType()),
                    csv(item.getAgentType()),
                    csv(item.getKnowledgeBaseId()),
                    csv(item.getFeedback()),
                    csv(item.getComment()),
                    csv(formatDateTime(item.getCreatedAt()))));
        }
        return joiner.toString();
    }

    public String exportEvidenceFeedbackCsv(int limit) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("responseId,chunkId,userId,knowledgeBaseId,feedback,comment,createdAt");
        for (EvidenceFeedbackSampleView item : getRecentEvidenceFeedback(limit)) {
            joiner.add(String.join(",",
                    csv(item.getResponseId()),
                    csv(item.getChunkId()),
                    csv(item.getUserId()),
                    csv(item.getKnowledgeBaseId()),
                    csv(item.getFeedback()),
                    csv(item.getComment()),
                    csv(formatDateTime(item.getCreatedAt()))));
        }
        return joiner.toString();
    }

    public String exportTopUsersCsv() {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("userId,agentType,calls,avgLatency");
        for (TopUserView item : getTopUsers()) {
            joiner.add(String.join(",",
                    csv(item.getUserId()),
                    csv(item.getAgentType()),
                    String.valueOf(item.getCalls()),
                    String.valueOf(item.getAvgLatency())));
        }
        return joiner.toString();
    }

    private AlertMetricsSnapshot buildAlertSnapshot() {
        try {
            String today = LocalDate.now().toString();
            var todayStats = jdbcTemplate.queryForMap(
                    "SELECT COUNT(*) as total, " +
                            "COUNT(*) FILTER (WHERE success = false) as errors, " +
                            "COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms), 0) as p95_latency " +
                            "FROM ai_audit_logs WHERE created_at >= ?::date",
                    today
            );

            long total = ((Number) todayStats.get("total")).longValue();
            long errors = ((Number) todayStats.get("errors")).longValue();

            return AlertMetricsSnapshot.builder()
                    .totalCount(total)
                    .errorCount(errors)
                    .errorRate(total > 0 ? (double) errors / total : 0)
                    .p95LatencyMs(((Number) todayStats.get("p95_latency")).doubleValue())
                    .tokenLimitExceeded(getCounterValue("ai.token.limit.exceeded"))
                    .activeRequests(getGaugeValue("ai.requests.active"))
                    .build();
        } catch (Exception e) {
            log.warn("Failed to build alert snapshot: {}", e.getMessage());
            return AlertMetricsSnapshot.builder()
                    .totalCount(0)
                    .errorCount(0)
                    .errorRate(0)
                    .p95LatencyMs(0)
                    .tokenLimitExceeded(0)
                    .activeRequests(0)
                    .build();
        }
    }

    private AuditLogView mapAuditLog(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return AuditLogView.builder()
                .id(rs.getString("id"))
                .userId(rs.getString("user_id"))
                .agentType(rs.getString("agent_type"))
                .modelId(rs.getString("model_id"))
                .errorMessage(rs.getString("error_message"))
                .sessionId(rs.getString("session_id"))
                .traceId(rs.getString("trace_id"))
                .latencyMs(rs.getLong("latency_ms"))
                .success(rs.getBoolean("success"))
                .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                .build();
    }

    private ToolAuditView mapToolAudit(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return ToolAuditView.builder()
                .id(rs.getString("id"))
                .userId(rs.getString("user_id"))
                .sessionId(rs.getString("session_id"))
                .agentType(rs.getString("agent_type"))
                .toolName(rs.getString("tool_name"))
                .toolClass(rs.getString("tool_class"))
                .inputSummary(rs.getString("input_summary"))
                .outputSummary(rs.getString("output_summary"))
                .success(rs.getBoolean("success"))
                .errorMessage(rs.getString("error_message"))
                .latencyMs(rs.getLong("latency_ms"))
                .traceId(rs.getString("trace_id"))
                .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                .build();
    }

    private List<ToolAuditView> getToolAuditsByTraceId(String traceId) {
        try {
            return jdbcTemplate.query(
                    "SELECT id, user_id, session_id, agent_type, tool_name, tool_class, input_summary, output_summary, " +
                            "success, error_message, latency_ms, trace_id, created_at " +
                            "FROM ai_tool_audit_logs WHERE trace_id = ? ORDER BY created_at ASC",
                    this::mapToolAudit,
                    traceId
            );
        } catch (Exception e) {
            log.warn("Failed to load tool audits for trace: traceId={}, error={}", traceId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<TracePhaseView> resolveTracePhases(String phaseBreakdownJson,
                                                    long totalLatencyMs,
                                                    List<ToolAuditView> toolExecutions,
                                                    boolean success) {
        List<TracePhaseView> parsed = parseTracePhases(phaseBreakdownJson);
        if (!parsed.isEmpty()) {
            return parsed;
        }
        return buildTracePhases(totalLatencyMs, toolExecutions, success);
    }

    private List<TracePhaseView> parseTracePhases(String phaseBreakdownJson) {
        if (phaseBreakdownJson == null || phaseBreakdownJson.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> items = objectMapper.readValue(
                    phaseBreakdownJson,
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );
            return items.stream()
                    .map(item -> TracePhaseView.builder()
                            .key(String.valueOf(item.getOrDefault("key", "")))
                            .label(String.valueOf(item.getOrDefault("label", "")))
                            .latencyMs(((Number) item.getOrDefault("latencyMs", 0)).longValue())
                            .share(((Number) item.getOrDefault("share", 0d)).doubleValue())
                            .estimated(Boolean.TRUE.equals(item.get("estimated")))
                            .description(String.valueOf(item.getOrDefault("description", "")))
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to parse trace phases: {}", e.getMessage());
            return List.of();
        }
    }

    private List<TracePhaseView> buildTracePhases(long totalLatencyMs, List<ToolAuditView> toolExecutions, boolean success) {
        if (totalLatencyMs <= 0) {
            return List.of();
        }

        long toolLatency = Math.min(totalLatencyMs, toolExecutions.stream()
                .mapToLong(item -> Math.max(item.getLatencyMs(), 0))
                .sum());

        long authLatency = clampEstimated(totalLatencyMs, 10, 35, 0.04d);
        long persistenceLatency = clampEstimated(totalLatencyMs, 8, 30, success ? 0.03d : 0.04d);
        long retrievalLatency = clampEstimated(totalLatencyMs, 12, 90, toolExecutions.isEmpty() ? 0.06d : 0.1d);

        long remaining = totalLatencyMs - authLatency - persistenceLatency - retrievalLatency - toolLatency;
        if (remaining < 20) {
            retrievalLatency = Math.max(0, retrievalLatency + remaining - 20);
            remaining = totalLatencyMs - authLatency - persistenceLatency - retrievalLatency - toolLatency;
        }
        long generationLatency = Math.max(20, remaining);

        long totalAssigned = authLatency + retrievalLatency + toolLatency + generationLatency + persistenceLatency;
        long adjust = totalLatencyMs - totalAssigned;
        generationLatency += adjust;

        List<TracePhaseView> phases = new ArrayList<>();
        phases.add(buildPhase("auth", "鉴权与上下文", authLatency, totalLatencyMs, true, "请求进入网关、鉴权校验和会话上下文装配。"));
        phases.add(buildPhase("retrieval", "检索与准备", retrievalLatency, totalLatencyMs, true, "检索知识、准备提示词和组织模型输入。"));
        phases.add(buildPhase("tools", "工具执行", toolLatency, totalLatencyMs, false, toolExecutions.isEmpty()
                ? "当前 Trace 没有记录到工具调用。"
                : "来自工具审计日志的实际累计耗时。"));
        phases.add(buildPhase("generation", "模型生成", generationLatency, totalLatencyMs, true, "模型推理、生成主回复内容。"));
        phases.add(buildPhase("persistence", "落库与审计", persistenceLatency, totalLatencyMs, true, "审计记录、反馈链路和结果持久化。"));
        return phases;
    }

    private TracePhaseView buildPhase(String key, String label, long latencyMs, long totalLatencyMs, boolean estimated, String description) {
        double share = totalLatencyMs <= 0 ? 0d : Math.round((latencyMs * 10000d / totalLatencyMs)) / 100d;
        return TracePhaseView.builder()
                .key(key)
                .label(label)
                .latencyMs(Math.max(latencyMs, 0))
                .share(share)
                .estimated(estimated)
                .description(description)
                .build();
    }

    private long clampEstimated(long totalLatencyMs, long min, long max, double ratio) {
        long estimate = Math.round(totalLatencyMs * ratio);
        return Math.max(min, Math.min(max, estimate));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    private String formatDateTime(LocalDateTime value) {
        return value != null ? value.format(CSV_TIME_FORMATTER) : "";
    }

    private String csv(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(value).toLocalDateTime();
    }

    private double getCounterValue(String name) {
        return meterRegistry.find(name).counters().stream().mapToDouble(counter -> counter.count()).sum();
    }

    private double getGaugeValue(String name) {
        var gauge = meterRegistry.find(name).gauge();
        return gauge != null ? gauge.value() : 0;
    }
}
