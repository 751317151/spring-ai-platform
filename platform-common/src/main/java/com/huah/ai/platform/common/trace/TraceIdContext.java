package com.huah.ai.platform.common.trace;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceIdContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String TRACE_ID_REQUEST_ATTRIBUTE = TraceIdContext.class.getName() + ".TRACE_ID";

    private TraceIdContext() {
    }

    public static String currentTraceId() {
        return MDC.get(TRACE_ID_MDC_KEY);
    }

    public static String resolveTraceId(String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            return candidate.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
