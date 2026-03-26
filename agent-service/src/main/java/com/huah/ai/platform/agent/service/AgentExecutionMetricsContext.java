package com.huah.ai.platform.agent.service;

public final class AgentExecutionMetricsContext {

    private static final ThreadLocal<AgentExecutionMetrics> CONTEXT = new ThreadLocal<>();

    private AgentExecutionMetricsContext() {
    }

    public static void set(AgentExecutionMetrics metrics) {
        CONTEXT.set(metrics);
    }

    public static AgentExecutionMetrics getAndClear() {
        AgentExecutionMetrics metrics = CONTEXT.get();
        CONTEXT.remove();
        return metrics;
    }
}
