package com.huah.ai.platform.agent.controller;

import com.huah.ai.platform.agent.dto.AgentErrorDetail;
import com.huah.ai.platform.agent.security.ToolAccessDeniedException;
import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.common.trace.TraceIdContext;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.huah.ai.platform.agent.controller")
public class AgentExceptionHandler {

    @ExceptionHandler(ToolAccessDeniedException.class)
    public Result<Object> handleToolAccessDenied(ToolAccessDeniedException ex) {
        return Result.fail(403, ex.getMessage(), AgentErrorDetail.builder()
                .errorCode("AGENT_TOOL_ACCESS_DENIED")
                .errorCategory("permission")
                .reasonCode(ex.getReasonCode())
                .resource(ex.getResource())
                .detail(ex.getDetail())
                .traceId(TraceIdContext.currentTraceId())
                .recoverable(Boolean.FALSE)
                .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Object> handleIllegalArgument(IllegalArgumentException ex) {
        return Result.fail(400, ex.getMessage(), AgentErrorDetail.builder()
                .errorCode("AGENT_BAD_REQUEST")
                .errorCategory("validation")
                .traceId(TraceIdContext.currentTraceId())
                .recoverable(Boolean.TRUE)
                .build());
    }
}
