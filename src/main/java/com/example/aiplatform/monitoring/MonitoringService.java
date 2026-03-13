package com.example.aiplatform.monitoring;

import com.example.aiplatform.dto.MonitoringSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class MonitoringService {

    private final Counter requestCounter;
    private final Counter errorCounter;
    private final AtomicLong tokens = new AtomicLong(0);
    private final AtomicLong anomalyCounter = new AtomicLong(0);
    private final Timer latencyTimer;

    public MonitoringService(MeterRegistry meterRegistry) {
        this.requestCounter = meterRegistry.counter("ai.requests.total");
        this.errorCounter = meterRegistry.counter("ai.requests.error");
        this.latencyTimer = meterRegistry.timer("ai.response.latency");
    }

    public void recordRequest(long latencyMs, boolean error, long tokenUsage, boolean anomaly) {
        requestCounter.increment();
        latencyTimer.record(java.time.Duration.ofMillis(latencyMs));
        tokens.addAndGet(tokenUsage);
        if (error) {
            errorCounter.increment();
        }
        if (anomaly) {
            anomalyCounter.incrementAndGet();
        }
    }

    public MonitoringSnapshot snapshot() {
        double total = requestCounter.count();
        double errors = errorCounter.count();
        double errorRate = total == 0 ? 0 : errors / total;
        return new MonitoringSnapshot(
                (long) total,
                errorRate,
                latencyTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS),
                tokens.get(),
                anomalyCounter.get()
        );
    }
}
