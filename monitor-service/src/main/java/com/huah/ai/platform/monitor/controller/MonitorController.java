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
 * 从 PostgreSQL 审计日志 + Redis Token 统计读取真实数据
 */
@RestController
@RequestMapping("/api/v1/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MeterRegistry meterRegistry;
    private final AiMetricsCollector metricsCollector;
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    /** 整体概览 -- 从 DB 读取真实统计 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();

        try {
            // 今日统计
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
            data.put("successRate", total > 0 ? (double)(total - errors) / total : 1.0);
            data.put("avgLatencyMs", ((Number) todayStats.get("avg_latency")).longValue());
            data.put("p95LatencyMs", ((Number) todayStats.get("p95_latency")).longValue());
            data.put("p99LatencyMs", ((Number) todayStats.get("p99_latency")).longValue());
            data.put("totalPromptTokens", promptTokens);
            data.put("totalCompletionTokens", completionTokens);
            data.put("totalTokens", promptTokens + completionTokens);

            // 当前活跃请求仍从 Micrometer Gauge 读取（实时指标）
            data.put("activeRequests", getGaugeValue("ai.requests.active"));

        } catch (Exception e) {
            // 表不存在或查询失败时返回零值
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

    /** 按 Agent 类型统计 -- 从 DB 读取 */
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

    /** 按小时统计 -- 延迟分布 + 错误率趋势（供图表使用） */
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

    /** 告警事件 -- 从 DB 统计异常情况 */
    @GetMapping("/alerts")
    public Result<Map<String, Object>> alerts() {
        List<Map<String, String>> activeAlerts = new ArrayList<>();

        try {
            String today = LocalDate.now().toString();

            // 从 DB 查询今日错误数
            Long errorCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_audit_logs WHERE success = false AND created_at >= ?::date",
                Long.class, today
            );
            if (errorCount == null) errorCount = 0L;

            // 从 DB 查询今日总请求
            Long totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_audit_logs WHERE created_at >= ?::date",
                Long.class, today
            );
            if (totalCount == null) totalCount = 0L;

            // 高延迟请求数 (>5s)
            Long highLatencyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_audit_logs WHERE latency_ms > 5000 AND created_at >= ?::date",
                Long.class, today
            );
            if (highLatencyCount == null) highLatencyCount = 0L;

            // Token 超限告警（从 Micrometer，如果有的话）
            long tokenLimitExceeded = (long) getCounterValue("ai.token.limit.exceeded");

            // 生成告警
            if (totalCount > 0 && errorCount * 100.0 / totalCount > 5) {
                activeAlerts.add(Map.of(
                    "level",   "ERROR",
                    "type",    "高错误率",
                    "message", "今日错误率 " + String.format("%.1f", errorCount * 100.0 / totalCount) + "% (" + errorCount + "/" + totalCount + ")，超过 5% 阈值",
                    "time",    LocalDateTime.now().toString()
                ));
            }

            if (errorCount > 10) {
                activeAlerts.add(Map.of(
                    "level",   "WARNING",
                    "type",    "错误请求累积",
                    "message", "今日累计 " + errorCount + " 次错误请求",
                    "time",    LocalDateTime.now().toString()
                ));
            }

            if (highLatencyCount > 0) {
                activeAlerts.add(Map.of(
                    "level",   "WARNING",
                    "type",    "高延迟告警",
                    "message", "今日有 " + highLatencyCount + " 次请求延迟超过 5 秒",
                    "time",    LocalDateTime.now().toString()
                ));
            }

            if (tokenLimitExceeded > 0) {
                activeAlerts.add(Map.of(
                    "level",   "WARNING",
                    "type",    "Token 超限",
                    "message", "今日已有 " + tokenLimitExceeded + " 次 Token 超限被拦截",
                    "time",    LocalDateTime.now().toString()
                ));
            }

            if (activeAlerts.isEmpty() && totalCount > 0) {
                activeAlerts.add(Map.of(
                    "level",   "INFO",
                    "type",    "系统正常",
                    "message", "今日共处理 " + totalCount + " 次请求，系统运行正常",
                    "time",    LocalDateTime.now().toString()
                ));
            }

        } catch (Exception e) {
            // DB 不可用时的降级
            activeAlerts.add(Map.of(
                "level",   "INFO",
                "type",    "暂无数据",
                "message", "监控数据尚未采集",
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
}
