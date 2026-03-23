package com.huah.ai.platform.agent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * agent-service 本地指标采集器。
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
                .tag("model", normalize(modelId))
                .tag("agent", normalize(agentType))
                .tag("success", String.valueOf(success))
                .register(registry)
                .record(latencyMs, TimeUnit.MILLISECONDS);

        Counter.builder("ai.requests.by_model")
                .tag("model", normalize(modelId))
                .tag("success", String.valueOf(success))
                .register(registry)
                .increment();

        log.debug("记录请求指标: agent={}, model={}, latency={}ms, success={}, tokens={}/{}",
                agentType, modelId, latencyMs, success, promptTokens, completionTokens);
    }

    public void recordModelCall(String agentType, long latencyMs, boolean success) {
        Timer.builder("ai.model.call.latency")
                .description("External model call latency")
                .tag("agent", normalize(agentType))
                .tag("success", String.valueOf(success))
                .publishPercentileHistogram()
                .register(registry)
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }

    public void recordDependencyFailure(String dependency, String operation) {
        Counter.builder("ai.dependency.failures")
                .description("Agent dependency failure count")
                .tag("dependency", normalize(dependency))
                .tag("operation", normalize(operation))
                .register(registry)
                .increment();
    }

    public void recordTokenLimitExceeded(String agentType) {
        Counter.builder("ai.token.limit.exceeded")
                .tag("agent", normalize(agentType))
                .register(registry)
                .increment();
    }

    public void incrementActive() {
        activeConcurrency.incrementAndGet();
    }

    public void decrementActive() {
        activeConcurrency.decrementAndGet();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
