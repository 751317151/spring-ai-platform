package com.huah.ai.platform.agent.audit;

import com.huah.ai.platform.common.trace.TraceIdContext;
import lombok.Builder;
import lombok.Value;

public final class ToolExecutionContext {

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    private ToolExecutionContext() {
    }

    public static void set(String userId, String sessionId, String agentType) {
        HOLDER.set(Context.builder()
                .userId(userId)
                .sessionId(sessionId)
                .agentType(agentType)
                .traceId(TraceIdContext.currentTraceId())
                .build());
    }

    public static Context current() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    @Value
    @Builder
    public static class Context {
        String userId;
        String sessionId;
        String agentType;
        String traceId;
    }
}
