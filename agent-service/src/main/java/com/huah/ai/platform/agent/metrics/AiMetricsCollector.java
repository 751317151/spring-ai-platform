package com.huah.ai.platform.agent.metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 平台自定义指标收集器（agent-service 本地）
 * 在实际处理 AI 请求的 JVM 内记录 Micrometer 指标，供 Prometheus 抓取。
 */
@Slf4j
@Component
public class AiMetricsCollector {

    private final MeterRegistry registry;

    private final Counter totalRequestsCounter;
    private final Counter errorRequestsCounter;
    private final Counter promptTokensCounter;
    private final Counter completionTokensCounter;
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

        Gauge.builder("ai.requests.active", activeConcurrency, AtomicLong::get)
                .description("当前活跃 AI 请求数")
                .register(registry);
    }

    /**
     * 记录一次 AI 调用的完整指标
     */
    public void recordRequest(String modelId, String agentType, long latencyMs,
                              boolean success, int promptTokens, int completionTokens) {
        totalRequestsCounter.increment();
        if (!success) {
            errorRequestsCounter.increment();
        }

        promptTokensCounter.increment(promptTokens);
        completionTokensCounter.increment(completionTokens);

        Timer.builder("ai.request.latency")
                .description("AI 请求响应延迟")
                .tag("model", modelId != null ? modelId : "unknown")
                .tag("agent", agentType != null ? agentType : "unknown")
                .tag("success", String.valueOf(success))
                .register(registry)
                .record(latencyMs, TimeUnit.MILLISECONDS);

        Counter.builder("ai.requests.by_model")
                .tag("model", modelId != null ? modelId : "unknown")
                .tag("success", String.valueOf(success))
                .register(registry)
                .increment();

        log.debug("记录指标: agent={}, model={}, latency={}ms, success={}, tokens={}/{}",
                agentType, modelId, latencyMs, success, promptTokens, completionTokens);
    }

    /** 记录 Token 超限 */
    public void recordTokenLimitExceeded(String agentType) {
        Counter.builder("ai.token.limit.exceeded")
                .tag("agent", agentType != null ? agentType : "unknown")
                .register(registry)
                .increment();
    }

    public void incrementActive() {
        activeConcurrency.incrementAndGet();
    }

    public void decrementActive() {
        activeConcurrency.decrementAndGet();
    }
}
