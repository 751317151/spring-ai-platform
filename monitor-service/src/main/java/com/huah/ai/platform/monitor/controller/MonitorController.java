package com.huah.ai.platform.monitor.controller;

import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.monitor.alert.AlertEvaluationService;
import com.huah.ai.platform.monitor.alert.AlertMetricsSnapshot;
import com.huah.ai.platform.monitor.alert.AlertmanagerAlertService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 监控仪表盘接口。
 */
@RestController
@RequestMapping("/api/v1/monitor")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class MonitorController {

    private final MeterRegistry meterRegistry;
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final AlertEvaluationService alertEvaluationService;
    private final AlertmanagerAlertService alertmanagerAlertService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();

        try {
            String today = LocalDate.now().toString();
            Map<String, Object> todayStats = jdbcTemplate.queryForMap(
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

            data.put("totalRequests", total);
            data.put("errorRequests", errors);
            data.put("successRate", total > 0 ? (double) (total - errors) / total : 1.0);
            data.put("avgLatencyMs", ((Number) todayStats.get("avg_latency")).longValue());
            data.put("p95LatencyMs", ((Number) todayStats.get("p95_latency")).longValue());
            data.put("p99LatencyMs", ((Number) todayStats.get("p99_latency")).longValue());
            data.put("totalPromptTokens", promptTokens);
            data.put("totalCompletionTokens", completionTokens);
            data.put("totalTokens", promptTokens + completionTokens);
            data.put("activeRequests", getGaugeValue("ai.requests.active"));
        } catch (Exception e) {
            data.put("totalRequests", 0);
            data.put("errorRequests", 0);
            data.put("successRate", 1.0);
            data.put("activeRequests", 0);
            data.put("totalPromptTokens", 0);
            data.put("totalCompletionTokens", 0);
            data.put("totalTokens", 0);
            data.put("avgLatencyMs", 0);
            data.put("p95LatencyMs", 0);
            data.put("p99LatencyMs", 0);
        }

        return Result.ok(data);
    }

    @GetMapping("/by-agent")
    public Result<List<Map<String, Object>>> byAgent() {
        try {
            String today = LocalDate.now().toString();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT agent_type, COUNT(*) as count, " +
                            "COALESCE(AVG(latency_ms), 0) as avg_latency, " +
                            "COUNT(*) FILTER (WHERE success = false) as errors " +
                            "FROM ai_audit_logs WHERE created_at >= ?::date " +
                            "GROUP BY agent_type ORDER BY count DESC",
                    today
            );
            return Result.ok(rows);
        } catch (Exception e) {
            return Result.ok(Collections.emptyList());
        }
    }

    @GetMapping("/token-usage/{userId}")
    public Result<Map<String, Object>> tokenUsage(@PathVariable(name = "userId") String userId) {
        String today = LocalDate.now().toString();
        String key = "ai:token:daily:" + userId + ":" + today;
        String usage = redisTemplate.opsForValue().get(key);
        return Result.ok(Map.of(
                "userId", userId,
                "date", today,
                "tokensUsed", usage != null ? Long.parseLong(usage) : 0L
        ));
    }

    @GetMapping("/audit-logs")
    public Result<List<Map<String, Object>>> auditLogs(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "userId", required = false) String userId) {
        try {
            String sql = userId != null
                    ? "SELECT id, user_id, agent_type, latency_ms, success, created_at " +
                    "FROM ai_audit_logs WHERE user_id = ? ORDER BY created_at DESC LIMIT ?"
                    : "SELECT id, user_id, agent_type, latency_ms, success, created_at " +
                    "FROM ai_audit_logs ORDER BY created_at DESC LIMIT ?";
            List<Map<String, Object>> rows = userId != null
                    ? jdbcTemplate.queryForList(sql, userId, limit)
                    : jdbcTemplate.queryForList(sql, limit);
            return Result.ok(rows);
        } catch (Exception e) {
            return Result.ok(Collections.emptyList());
        }
    }

    @GetMapping("/token-top-users")
    public Result<List<Map<String, Object>>> tokenTopUsers() {
        try {
            String sql = "SELECT user_id, agent_type, COUNT(*) as calls, " +
                    "AVG(latency_ms) as avg_latency " +
                    "FROM ai_audit_logs WHERE created_at > ? " +
                    "GROUP BY user_id, agent_type ORDER BY calls DESC LIMIT 10";
            return Result.ok(jdbcTemplate.queryForList(sql, LocalDateTime.now().minusDays(1)));
        } catch (Exception e) {
            return Result.ok(Collections.emptyList());
        }
    }

    @GetMapping("/hourly-stats")
    public Result<List<Map<String, Object>>> hourlyStats() {
        try {
            String today = LocalDate.now().toString();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT EXTRACT(HOUR FROM created_at)::int as hour, " +
                            "COUNT(*) as total, " +
                            "COUNT(*) FILTER (WHERE success = false) as errors, " +
                            "COALESCE(AVG(latency_ms), 0) as avg_latency, " +
                            "COALESCE(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY latency_ms), 0) as p50, " +
                            "COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms), 0) as p95 " +
                            "FROM ai_audit_logs WHERE created_at >= ?::date " +
                            "GROUP BY EXTRACT(HOUR FROM created_at) ORDER BY hour",
                    today
            );
            return Result.ok(rows);
        } catch (Exception e) {
            return Result.ok(Collections.emptyList());
        }
    }

    @GetMapping("/alerts")
    public Result<Map<String, Object>> alerts() {
        var alerts = alertmanagerAlertService.fetchActiveAlerts();
        if (alerts.isEmpty()) {
            AlertMetricsSnapshot snapshot = buildAlertSnapshot();
            alerts = alertEvaluationService.evaluate(snapshot);
        }
        return Result.ok(Map.of(
                "activeAlerts", alerts.stream().filter(a -> !"INFO".equals(a.getLevel())).count(),
                "alerts", alerts
        ));
    }

    private AlertMetricsSnapshot buildAlertSnapshot() {
        try {
            String today = LocalDate.now().toString();
            Map<String, Object> todayStats = jdbcTemplate.queryForMap(
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

    private double getCounterValue(String name) {
        return meterRegistry.find(name).counters().stream().mapToDouble(counter -> counter.count()).sum();
    }

    private double getGaugeValue(String name) {
        var gauge = meterRegistry.find(name).gauge();
        return gauge != null ? gauge.value() : 0;
    }
}
