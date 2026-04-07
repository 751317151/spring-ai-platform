package com.huah.ai.platform.monitor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huah.ai.platform.monitor.model.AuditLogResponse;
import com.huah.ai.platform.monitor.model.ToolAuditResponse;
import com.huah.ai.platform.monitor.model.TraceDetailResponse;
import com.huah.ai.platform.monitor.model.TracePhaseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorTraceQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public List<AuditLogResponse> getAuditLogs(int limit, String userId) {
        try {
            String sql = userId != null
                    ? "SELECT id, user_id, agent_type, model_id, error_message, client_ip, country, province, city, session_id, trace_id, latency_ms, success, created_at " +
                    "FROM ai_audit_logs WHERE user_id = ? ORDER BY created_at DESC LIMIT ?"
                    : "SELECT id, user_id, agent_type, model_id, error_message, client_ip, country, province, city, session_id, trace_id, latency_ms, success, created_at " +
                    "FROM ai_audit_logs ORDER BY created_at DESC LIMIT ?";
            return userId != null
                    ? jdbcTemplate.query(sql, this::mapAuditLog, userId, limit)
                    : jdbcTemplate.query(sql, this::mapAuditLog, limit);
        } catch (RuntimeException e) {
            log.warn("Failed to load audit logs: userId={}, limit={}, error={}", userId, limit, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<ToolAuditResponse> getToolAudits(int limit, String userId, String agentType, String toolName) {
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT id, user_id, session_id, agent_type, tool_name, tool_class, input_summary, output_summary, " +
                            "success, error_message, latency_ms, trace_id, created_at FROM ai_tool_audit_logs WHERE 1 = 1"
            );
            List<Object> args = new ArrayList<>();
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
        } catch (RuntimeException e) {
            log.warn("Failed to load tool audits: userId={}, agentType={}, toolName={}, limit={}, error={}",
                    userId, agentType, toolName, limit, e.getMessage());
            return Collections.emptyList();
        }
    }

    public Optional<TraceDetailResponse> getTraceDetail(String traceId) {
        try {
            List<TraceDetailResponse> rows = jdbcTemplate.query(
                    "SELECT id, trace_id, user_id, agent_type, model_id, session_id, success, error_message, " +
                            "latency_ms, prompt_tokens, completion_tokens, created_at, user_message, ai_response, phase_breakdown_json " +
                            "FROM ai_audit_logs WHERE trace_id = ? ORDER BY created_at DESC LIMIT 1",
                    (rs, rowNum) -> {
                        List<ToolAuditResponse> toolExecutions = getToolAuditsByTraceId(traceId);
                        long latencyMs = rs.getLong("latency_ms");
                        String phaseBreakdownJson = rs.getString("phase_breakdown_json");
                        return TraceDetailResponse.builder()
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
        } catch (RuntimeException e) {
            log.warn("Failed to load trace detail: traceId={}, error={}", traceId, e.getMessage());
            return Optional.empty();
        }
    }

    private AuditLogResponse mapAuditLog(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return AuditLogResponse.builder()
                .id(rs.getString("id"))
                .userId(rs.getString("user_id"))
                .agentType(rs.getString("agent_type"))
                .modelId(rs.getString("model_id"))
                .errorMessage(rs.getString("error_message"))
                .clientIp(rs.getString("client_ip"))
                .country(rs.getString("country"))
                .province(rs.getString("province"))
                .city(rs.getString("city"))
                .sessionId(rs.getString("session_id"))
                .traceId(rs.getString("trace_id"))
                .latencyMs(rs.getLong("latency_ms"))
                .success(rs.getBoolean("success"))
                .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                .build();
    }

    private ToolAuditResponse mapToolAudit(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return ToolAuditResponse.builder()
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

    private List<ToolAuditResponse> getToolAuditsByTraceId(String traceId) {
        try {
            return jdbcTemplate.query(
                    "SELECT id, user_id, session_id, agent_type, tool_name, tool_class, input_summary, output_summary, " +
                            "success, error_message, latency_ms, trace_id, created_at " +
                            "FROM ai_tool_audit_logs WHERE trace_id = ? ORDER BY created_at ASC",
                    this::mapToolAudit,
                    traceId
            );
        } catch (RuntimeException e) {
            log.warn("Failed to load tool audits for trace: traceId={}, error={}", traceId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<TracePhaseResponse> resolveTracePhases(String phaseBreakdownJson,
                                                    long totalLatencyMs,
                                                    List<ToolAuditResponse> toolExecutions,
                                                    boolean success) {
        List<TracePhaseResponse> parsed = parseTracePhases(phaseBreakdownJson);
        if (!parsed.isEmpty()) {
            return parsed;
        }
        return buildTracePhases(totalLatencyMs, toolExecutions, success);
    }

    private List<TracePhaseResponse> parseTracePhases(String phaseBreakdownJson) {
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
                    .map(item -> TracePhaseResponse.builder()
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

        private List<TracePhaseResponse> buildTracePhases(long totalLatencyMs, List<ToolAuditResponse> toolExecutions, boolean success) {
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

        List<TracePhaseResponse> phases = new ArrayList<>();
        phases.add(buildPhase("auth", "鉴权与上下文", authLatency, totalLatencyMs, true, "请求进入网关后完成鉴权校验与会话上下文装配。"));
        phases.add(buildPhase("retrieval", "检索与准备", retrievalLatency, totalLatencyMs, true, "检索知识、准备提示词，并组织模型输入内容。"));
        phases.add(buildPhase("tools", "工具执行", toolLatency, totalLatencyMs, false, toolExecutions.isEmpty()
                ? "当前 Trace 没有记录到工具调用。"
                : "来自工具审计日志的实际累计耗时。"));
        phases.add(buildPhase("generation", "模型生成", generationLatency, totalLatencyMs, true, "模型生成回复、推理结果和最终输出。"));
        phases.add(buildPhase("persistence", "落库与审计", persistenceLatency, totalLatencyMs, true, "审计记录、反馈链路和结果持久化。"));
        return phases;
    }

    private TracePhaseResponse buildPhase(String key,
                                      String label,
                                      long latencyMs,
                                      long totalLatencyMs,
                                      boolean estimated,
                                      String description) {
        double share = totalLatencyMs <= 0 ? 0d : Math.round((latencyMs * 10000d / totalLatencyMs)) / 100d;
        return TracePhaseResponse.builder()
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
}
