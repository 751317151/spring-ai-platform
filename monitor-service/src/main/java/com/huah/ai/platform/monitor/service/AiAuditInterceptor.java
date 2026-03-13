package com.huah.ai.platform.monitor.service;

import com.huah.ai.platform.monitor.metrics.AiMetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AiAuditInterceptor — monitor-service 保留为空壳
 *
 * ⚠️ AOP @Around 切点只能拦截同一 Spring ApplicationContext 内的 Bean。
 *    monitor-service 与 agent-service 是独立进程，跨进程 AOP 不生效。
 *
 * 审计逻辑已迁移至：
 *   agent-service/audit/AgentAuditAspect.java  ← 真正生效的 AOP
 *   agent-service/audit/AiAuditLog.java        ← 实体 + Repository
 *
 * monitor-service 通过 REST 调用 agent-service 的审计日志查询接口，
 * 或者直接读取共享 PostgreSQL 数据库中的 ai_audit_logs 表。
 *
 * Metrics 仍由 AiMetricsCollector 通过 Micrometer 上报 Prometheus。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAuditInterceptor {

    private final AiMetricsCollector metricsCollector;

    /**
     * 供 monitor-service 内部其他组件调用的指标记录入口
     * （非 AOP，而是直接调用）
     */
    public void recordMetric(String model, String agent, String scene,
                              long latencyMs, boolean success,
                              int promptTokens, int completionTokens) {
        metricsCollector.recordRequest(model, agent, scene, latencyMs,
                success, promptTokens, completionTokens);
    }
}
