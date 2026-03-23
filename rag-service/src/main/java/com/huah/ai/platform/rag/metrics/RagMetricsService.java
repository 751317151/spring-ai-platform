package com.huah.ai.platform.rag.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RagMetricsService {

    private final MeterRegistry meterRegistry;

    public void recordDependencyFailure(String dependency, String operation) {
        Counter.builder("rag.dependency.failures")
                .description("RAG dependency failure count")
                .tag("dependency", dependency)
                .tag("operation", operation)
                .register(meterRegistry)
                .increment();
    }

    public void recordStageLatency(String stage, long elapsedMs, boolean success) {
        Timer.builder("rag.stage.latency")
                .description("RAG processing stage latency")
                .tag("stage", stage)
                .tag("success", String.valueOf(success))
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(elapsedMs, TimeUnit.MILLISECONDS);
    }
}
