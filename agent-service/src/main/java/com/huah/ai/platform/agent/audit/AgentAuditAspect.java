package com.huah.ai.platform.agent.audit;

import com.huah.ai.platform.agent.metrics.AiMetricsCollector;
import com.huah.ai.platform.agent.service.AgentChatResult;
import com.huah.ai.platform.common.persistence.audit.AiAuditLogEntity;
import com.huah.ai.platform.common.persistence.audit.AiAuditLogMapper;
import com.huah.ai.platform.common.trace.TraceIdContext;
import com.huah.ai.platform.common.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.LocalDateTime;

/**
 * AI 对话审计切面。
 *
 * 切点：所有 agent service 的 `.chat()` 方法。
 * 记录：调用方、入参与响应摘要、耗时、成功状态、Token 用量。
 * 指标：同步写入 Micrometer，供 Prometheus 等监控系统采集。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AgentAuditAspect {

    private final AiAuditLogMapper auditLogMapper;
    private final AiMetricsCollector metricsCollector;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Around("execution(* com.huah.ai.platform.agent.service.*.chat(String, String, String))")
    public Object auditChat(ProceedingJoinPoint pjp) throws Throwable {
        if (RequestContextHolder.getRequestAttributes() != null) {
            return pjp.proceed();
        }
        long start = System.currentTimeMillis();
        Object[] args = pjp.getArgs();
        String userId = args.length > 0 ? (String) args[0] : "unknown";
        String sessionId = args.length > 1 ? (String) args[1] : "unknown";
        String userMsg = args.length > 2 ? truncate((String) args[2], 500) : "";
        String agentType = pjp.getTarget().getClass().getSimpleName();

        boolean success = true;
        String errorMsg = null;
        AgentChatResult chatResult = null;

        metricsCollector.incrementActive();
        try {
            chatResult = (AgentChatResult) pjp.proceed();
            return chatResult;
        } catch (Throwable e) {
            success = false;
            errorMsg = e.getMessage();
            throw e;
        } finally {
            metricsCollector.decrementActive();
            long latency = System.currentTimeMillis() - start;
            int promptTokens = chatResult != null ? chatResult.getPromptTokens() : 0;
            int completionTokens = chatResult != null ? chatResult.getCompletionTokens() : 0;

            metricsCollector.recordRequest(null, agentType, latency, success, promptTokens, completionTokens);

            try {
                String responseText = chatResult != null ? chatResult.getContent() : null;
                auditLogMapper.insert(AiAuditLogEntity.builder()
                        .id(snowflakeIdGenerator.nextLongId())
                        .userId(userId)
                        .sessionId(sessionId)
                        .agentType(agentType)
                        .userMessage(userMsg)
                        .aiResponse(responseText != null ? truncate(responseText, 500) : null)
                        .promptTokens(promptTokens)
                        .completionTokens(completionTokens)
                        .latencyMs(latency)
                        .success(success)
                        .errorMessage(errorMsg)
                        .traceId(TraceIdContext.currentTraceId())
                        .createdAt(LocalDateTime.now())
                        .build());
            } catch (Exception ex) {
                log.warn("写入 AI 审计日志失败: {}", ex.getMessage());
            }
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
