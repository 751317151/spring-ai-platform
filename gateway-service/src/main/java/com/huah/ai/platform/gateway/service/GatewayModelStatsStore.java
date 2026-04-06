package com.huah.ai.platform.gateway.service;

import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class GatewayModelStatsStore {

    private final JdbcTemplate jdbcTemplate;

    GatewayModelStatsStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Map<String, ModelGatewayService.ModelStats> loadStats() {
        Map<String, ModelGatewayService.ModelStats> loadedStats = new ConcurrentHashMap<>();
        jdbcTemplate.query(
                "SELECT model_id, total_calls, success_calls, total_latency_ms, total_prompt_tokens, total_completion_tokens, total_estimated_cost FROM gateway_model_stats",
                rs -> {
                    String modelId = rs.getString("model_id");
                    int totalCalls = rs.getInt("total_calls");
                    int successCalls = rs.getInt("success_calls");
                    long totalLatencyMs = rs.getLong("total_latency_ms");
                    long totalPromptTokens = rs.getLong("total_prompt_tokens");
                    long totalCompletionTokens = rs.getLong("total_completion_tokens");
                    double totalEstimatedCost = rs.getDouble("total_estimated_cost");

                    ModelGatewayService.ModelStats stats =
                            loadedStats.computeIfAbsent(modelId, ModelGatewayService.ModelStats::new);
                    stats.restore(
                            totalCalls,
                            successCalls,
                            totalLatencyMs,
                            totalPromptTokens,
                            totalCompletionTokens,
                            totalEstimatedCost
                    );
                });
        return loadedStats;
    }

    void persistStats(String modelId, ModelGatewayService.ModelStats stats) {
        int total = stats.getTotalCalls().get();
        int success = stats.getSuccessCalls().get();
        long totalLatency = stats.getTotalLatencyMs();
        long totalPromptTokens = stats.getTotalPromptTokens();
        long totalCompletionTokens = stats.getTotalCompletionTokens();
        double totalEstimatedCost = stats.getTotalEstimatedCost();

        jdbcTemplate.update("""
            INSERT INTO gateway_model_stats (model_id, total_calls, success_calls, total_latency_ms, total_prompt_tokens, total_completion_tokens, total_estimated_cost, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (model_id) DO UPDATE SET
                total_calls = EXCLUDED.total_calls,
                success_calls = EXCLUDED.success_calls,
                total_latency_ms = EXCLUDED.total_latency_ms,
                total_prompt_tokens = EXCLUDED.total_prompt_tokens,
                total_completion_tokens = EXCLUDED.total_completion_tokens,
                total_estimated_cost = EXCLUDED.total_estimated_cost,
                updated_at = NOW()
            """, modelId, total, success, totalLatency, totalPromptTokens, totalCompletionTokens, totalEstimatedCost);
    }

    boolean isRecoverableDataAccessException(RuntimeException exception) {
        return exception instanceof BadSqlGrammarException
                || exception instanceof UncategorizedSQLException;
    }
}
