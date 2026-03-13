package com.huah.ai.platform.monitor.metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 平台自定义指标收集器
 * 收集：响应延迟、Token消耗、错误率、并发数
 */
@Slf4j
@Component
public class AiMetricsCollector {

    private final MeterRegistry registry;

    // 请求计数器
    private final Counter totalRequestsCounter;
    private final Counter errorRequestsCounter;

    // Token 消耗计数器
    private final Counter promptTokensCounter;
    private final Counter completionTokensCounter;

    // 当前并发数
    private final AtomicLong activeConcurrency = new AtomicLong(0);

    public AiMetricsCollector(MeterRegistry registry) {
        this.registry = registry;

        this.totalRequestsCounter = Counter.builder("ai.requests.total")
                .description("AI 请求总数")
                .register(registry);

        this.errorRequestsCounter = Counter.builder("ai.requests.errors")
                .description("AI 请求错误数")
                .register(registry);

        this.promptTokensCounter = Counter.builder("ai.tokens.prompt")
                .description("Prompt Token 消耗总量")
                .register(registry);

        this.completionTokensCounter = Counter.builder("ai.tokens.completion")
                .description("Completion Token 消耗总量")
                .register(registry);

        // 注册 Gauge（当前并发）
        Gauge.builder("ai.requests.active", activeConcurrency, AtomicLong::get)
                .description("当前活跃 AI 请求数")
                .register(registry);
    }

    /**
     * 记录一次 AI 调用
     */
    public void recordRequest(String modelId, String agentType, String scene,
                               long latencyMs, boolean success,
                               int promptTokens, int completionTokens) {
        // 计数
        totalRequestsCounter.increment();
        if (!success) errorRequestsCounter.increment();

        // Token 消耗
        promptTokensCounter.increment(promptTokens);
        completionTokensCounter.increment(completionTokens);

        // 响应延迟分布（Histogram）
        Timer.builder("ai.request.latency")
                .description("AI 请求响应延迟")
                .tag("model", modelId)
                .tag("agent", agentType)
                .tag("scene", scene)
                .tag("success", String.valueOf(success))
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);

        // 按模型分别计数
        Counter.builder("ai.requests.by_model")
                .tag("model", modelId)
                .tag("success", String.valueOf(success))
                .register(registry)
                .increment();

        log.debug("记录指标: model={}, latency={}ms, success={}, tokens={}/{}",
                modelId, latencyMs, success, promptTokens, completionTokens);
    }

    /**
     * 记录异常查询（敏感词、越权等）
     */
    public void recordAnomalyQuery(String userId, String agentType, String anomalyType) {
        Counter.builder("ai.anomaly.queries")
                .description("异常查询次数")
                .tag("agent", agentType)
                .tag("type", anomalyType)
                .register(registry)
                .increment();

        log.warn("检测到异常查询: userId={}, agent={}, type={}", userId, agentType, anomalyType);
    }

    /** 记录 Token 超限 */
    public void recordTokenLimitExceeded(String userId, String agentType) {
        Counter.builder("ai.token.limit.exceeded")
                .tag("agent", agentType)
                .register(registry)
                .increment();
        log.warn("Token 超限: userId={}, agent={}", userId, agentType);
    }

    public void incrementActive() { activeConcurrency.incrementAndGet(); }
    public void decrementActive() { activeConcurrency.decrementAndGet(); }
}
