package com.huah.ai.platform.agent.audit;

import com.huah.ai.platform.agent.metrics.AiMetricsCollector;
import com.huah.ai.platform.agent.service.AgentChatResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AI 调用审计 AOP 拦截器
 *
 * 切点：所有 agent service 的 .chat() 方法
 * 记录：调用方、入参摘要、响应摘要、延迟、是否成功、精确 token 用量
 * 同时向 Micrometer 注册实时指标供 Prometheus 抓取
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AgentAuditAspect {

    private final AiAuditLogMapper auditLogMapper;
    private final AiMetricsCollector metricsCollector;

    @Around("execution(* com.huah.ai.platform.agent.service.*.chat(String, String, String))")
    public Object auditChat(ProceedingJoinPoint pjp) throws Throwable {
        if (RequestContextHolder.getRequestAttributes() != null) {
            return pjp.proceed();
        }
        long start = System.currentTimeMillis();
        Object[] args = pjp.getArgs();
        String userId    = args.length > 0 ? (String) args[0] : "unknown";
        String sessionId = args.length > 1 ? (String) args[1] : "unknown";
        String userMsg   = args.length > 2 ? truncate((String) args[2], 500) : "";
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

            // 记录 Prometheus 指标
            metricsCollector.recordRequest(null, agentType, latency, success, promptTokens, completionTokens);

            // 写入审计日志
            try {
                String responseText = chatResult != null ? chatResult.getContent() : null;
                auditLogMapper.insert(AiAuditLog.builder()
                        .id(UUID.randomUUID().toString())
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
                        .createdAt(LocalDateTime.now())
                        .build());
            } catch (Exception ex) {
                log.warn("审计日志写入失败: {}", ex.getMessage());
            }
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
