package com.huah.ai.platform.monitor.service;

import com.huah.ai.platform.monitor.alert.AlertMetricsSnapshot;
import com.huah.ai.platform.monitor.model.AgentStatView;
import com.huah.ai.platform.monitor.model.FailureSampleView;
import com.huah.ai.platform.monitor.model.HourlyStatView;
import com.huah.ai.platform.monitor.model.ModelStatView;
import com.huah.ai.platform.monitor.model.MonitorOverviewView;
import com.huah.ai.platform.monitor.model.SlowRequestView;
import com.huah.ai.platform.monitor.model.TokenUsageView;
import com.huah.ai.platform.monitor.model.TopUserView;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorMetricsQueryService {

    private final MeterRegistry meterRegistry;
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

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
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
            log.warn("Failed to load failure samples: limit={}, error={}", limit, e.getMessage());
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
        } catch (RuntimeException e) {
            log.warn("Failed to load hourly stats: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public AlertMetricsSnapshot buildAlertSnapshot() {
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
        } catch (RuntimeException e) {
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

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    private double getCounterValue(String name) {
        return meterRegistry.find(name).counters().stream().mapToDouble(counter -> counter.count()).sum();
    }

    private double getGaugeValue(String name) {
        var gauge = meterRegistry.find(name).gauge();
        return gauge != null ? gauge.value() : 0;
    }
}
