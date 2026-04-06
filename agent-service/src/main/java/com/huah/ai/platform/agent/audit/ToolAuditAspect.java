package com.huah.ai.platform.agent.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huah.ai.platform.agent.security.ToolAccessDeniedException;
import com.huah.ai.platform.agent.security.ToolSecurityService;
import com.huah.ai.platform.common.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ToolAuditAspect {

    private static final int MAX_SUMMARY_LENGTH = 1000;

    private final AiToolAuditLogMapper toolAuditLogMapper;
    private final ObjectMapper objectMapper;
    private final ToolSecurityService toolSecurityService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Around("@annotation(tool)")
    public Object auditTool(ProceedingJoinPoint pjp, Tool tool) throws Throwable {
        long start = System.currentTimeMillis();
        boolean success = true;
        String errorMessage = null;
        String reasonCode = null;
        String deniedResource = null;
        Object result = null;
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        String toolName = resolveToolName(method, tool);

        try {
            toolSecurityService.validateToolAccess(toolName, ToolExecutionContext.current());
            result = pjp.proceed();
            return result;
        } catch (Throwable ex) {
            success = false;
            errorMessage = ex.getMessage();
            if (ex instanceof ToolAccessDeniedException deniedException) {
                reasonCode = deniedException.getReasonCode();
                deniedResource = deniedException.getResource();
            }
            throw ex;
        } finally {
            persistAuditLog(pjp, toolName, success, errorMessage, reasonCode, deniedResource, result, System.currentTimeMillis() - start);
        }
    }

    private void persistAuditLog(ProceedingJoinPoint pjp,
                                 String toolName,
                                 boolean success,
                                 String errorMessage,
                                 String reasonCode,
                                 String deniedResource,
                                 Object result,
                                 long latencyMs) {
        ToolExecutionContext.Context context = ToolExecutionContext.current();
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();

        try {
            toolAuditLogMapper.insert(AiToolAuditLogEntity.builder()
                    .id(snowflakeIdGenerator.nextLongId())
                    .userId(context != null ? context.getUserId() : null)
                    .sessionId(context != null ? context.getSessionId() : "unknown")
                    .agentType(context != null ? context.getAgentType() : "unknown")
                    .toolName(toolName)
                    .toolClass(pjp.getTarget().getClass().getSimpleName())
                    .inputSummary(summarizeInputs(method, pjp.getArgs()))
                    .outputSummary(summarizeValue(result))
                    .success(success)
                    .errorMessage(truncate(errorMessage))
                    .reasonCode(truncate(reasonCode))
                    .deniedResource(truncate(deniedResource))
                    .latencyMs(latencyMs)
                    .traceId(context != null ? context.getTraceId() : null)
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (Exception ex) {
            log.warn("tool audit log write failed: {}", ex.getMessage());
        }
    }

    private String resolveToolName(Method method, Tool tool) {
        if (tool != null && tool.name() != null && !tool.name().isBlank()) {
            return tool.name();
        }
        return method.getName();
    }

    private String summarizeInputs(Method method, Object[] args) {
        Map<String, Object> summary = new LinkedHashMap<>();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            String name = parameters[i].getName();
            summary.put(name != null ? name : "arg" + i, safeValue(args != null && i < args.length ? args[i] : null));
        }
        return writeSummary(summary);
    }

    private String summarizeValue(Object value) {
        return writeSummary(safeValue(value));
    }

    private Object safeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence sequence) {
            return truncate(sequence.toString());
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return truncate(String.valueOf(value));
    }

    private String writeSummary(Object value) {
        try {
            return truncate(objectMapper.writeValueAsString(value));
        } catch (Exception e) {
            return truncate(String.valueOf(value));
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_SUMMARY_LENGTH
                ? text
                : text.substring(0, MAX_SUMMARY_LENGTH) + "...";
    }
}

