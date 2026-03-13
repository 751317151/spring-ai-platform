package com.huah.ai.platform.monitor.controller;

import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.monitor.metrics.AiMetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 监控仪表板接口
 * 读取 Micrometer 指标 + Redis Token 统计 + PostgreSQL 审计日志
 */
@RestController
@RequestMapping("/api/v1/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MeterRegistry meterRegistry;
    private final AiMetricsCollector metricsCollector;
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    /** 整体概览 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();
        data.put("totalRequests",        getCounterValue("ai.requests.total"));
        data.put("errorRequests",         getCounterValue("ai.requests.errors"));
        data.put("successRate",           calcSuccessRate());
        data.put("activeRequests",        getGaugeValue("ai.requests.active"));
        data.put("totalPromptTokens",     getCounterValue("ai.tokens.prompt"));
        data.put("totalCompletionTokens", getCounterValue("ai.tokens.completion"));
        data.put("totalTokens",           getCounterValue("ai.tokens.prompt") +
                                          getCounterValue("ai.tokens.completion"));

        var timer = meterRegistry.find("ai.request.latency").timer();
        if (timer != null) {
            data.put("avgLatencyMs",  (long) timer.mean(TimeUnit.MILLISECONDS));
            data.put("p95LatencyMs",  (long) timer.percentile(0.95, TimeUnit.MILLISECONDS));
            data.put("p99LatencyMs",  (long) timer.percentile(0.99, TimeUnit.MILLISECONDS));
        } else {
            data.put("avgLatencyMs", 0); data.put("p95LatencyMs", 0); data.put("p99LatencyMs", 0);
        }
        return Result.ok(data);
    }

    /** 按 Agent 类型统计 */
    @GetMapping("/by-agent")
    public Result<Map<String, Object>> byAgent() {
        Map<String, Object> result = new HashMap<>();
        String[] agents = {"RdAssistantAgent","SalesAssistantAgent","HrAssistantAgent",
                           "FinanceAssistantAgent","SupplyChainAgent","QcAssistantAgent"};
        for (String agent : agents) {
            var timer = meterRegistry.find("ai.request.latency").tag("agent", agent).timer();
            if (timer != null) {
                result.put(agent, Map.of(
                    "count",      timer.count(),
                    "avgLatency", (long) timer.mean(TimeUnit.MILLISECONDS)
                ));
            }
        }
        return Result.ok(result);
    }

    /** 用户 Token 消耗查询 */
    @GetMapping("/token-usage/{userId}")
    public Result<Map<String, Object>> tokenUsage(@PathVariable String userId) {
        String today = LocalDate.now().toString();
        String key   = "ai:token:daily:" + userId + ":" + today;
        String usage = redisTemplate.opsForValue().get(key);
        return Result.ok(Map.of(
            "userId",     userId,
            "date",       today,
            "tokensUsed", usage != null ? Long.parseLong(usage) : 0L
        ));
    }

    /** 最近审计日志（读 DB） */
    @GetMapping("/audit-logs")
    public Result<List<Map<String, Object>>> auditLogs(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String userId) {
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

    /** Token 消耗 Top 10（读 DB） */
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

    /** 告警事件（从 Redis 读取限流/熔断告警，从 DB 读超限事件） */
    @GetMapping("/alerts")
    public Result<Map<String, Object>> alerts() {
        long tokenLimitExceeded = (long) getCounterValue("ai.token.limit.exceeded");
        long anomalyQueries     = (long) getCounterValue("ai.anomaly.queries");
        long errorCount         = (long) getCounterValue("ai.requests.errors");

        List<Map<String, String>> activeAlerts = new ArrayList<>();
        if (tokenLimitExceeded > 0) {
            activeAlerts.add(Map.of(
                "level",   "WARNING",
                "type",    "TOKEN_LIMIT_EXCEEDED",
                "message", "今日已有 " + tokenLimitExceeded + " 次 Token 超限被拦截",
                "time",    LocalDateTime.now().toString()
            ));
        }
        if (anomalyQueries > 0) {
            activeAlerts.add(Map.of(
                "level",   "WARNING",
                "type",    "ANOMALY_QUERY",
                "message", "检测到 " + anomalyQueries + " 次异常查询",
                "time",    LocalDateTime.now().toString()
            ));
        }
        if (errorCount > 10) {
            activeAlerts.add(Map.of(
                "level",   "ERROR",
                "type",    "HIGH_ERROR_RATE",
                "message", "错误请求数已达 " + (long)errorCount + " 次",
                "time",    LocalDateTime.now().toString()
            ));
        }

        return Result.ok(Map.of(
            "activeAlerts", activeAlerts.size(),
            "alerts",       activeAlerts
        ));
    }

    // ===== helpers =====
    private double getCounterValue(String name) {
        var c = meterRegistry.find(name).counter();
        return c != null ? c.count() : 0;
    }
    private double getGaugeValue(String name) {
        var g = meterRegistry.find(name).gauge();
        return g != null ? g.value() : 0;
    }
    private double calcSuccessRate() {
        double total  = getCounterValue("ai.requests.total");
        double errors = getCounterValue("ai.requests.errors");
        return total > 0 ? (total - errors) / total : 1.0;
    }
}
