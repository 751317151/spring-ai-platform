package com.huah.ai.platform.monitor.service;

import com.huah.ai.platform.monitor.metrics.AiMetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * monitor-service 中保留的审计指标写入入口。
 * AOP 只能拦截同一 Spring 容器内的 Bean，无法跨服务拦截 agent-service。
 * 真正生效的审计切面位于 agent-service，这里只负责提供指标记录方法。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAuditInterceptor {

    private final AiMetricsCollector metricsCollector;

    /**
     * 供 monitor-service 内部组件直接记录指标，非 AOP 拦截。
     */
    public void recordMetric(String model, String agent, String scene,
                             long latencyMs, boolean success,
                             int promptTokens, int completionTokens) {
        metricsCollector.recordRequest(model, agent, scene, latencyMs,
                success, promptTokens, completionTokens);
    }
}
