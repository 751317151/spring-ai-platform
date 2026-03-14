package com.huah.ai.platform.agent.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AI 调用审计 AOP 拦截器
 *
 * 切点：所有 agent service 的 .chat() 方法
 * 记录：调用方、入参摘要、响应摘要、延迟、是否成功
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AgentAuditAspect {

    private final AiAuditLogMapper auditLogMapper;

    @Around("execution(* com.huah.ai.platform.agent.service.*.chat(String, String, String))")
    public Object auditChat(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        Object[] args = pjp.getArgs();
        String userId    = args.length > 0 ? (String) args[0] : "unknown";
        String sessionId = args.length > 1 ? (String) args[1] : "unknown";
        String userMsg   = args.length > 2 ? truncate((String) args[2], 500) : "";
        String agentType = pjp.getTarget().getClass().getSimpleName();

        boolean success = true;
        String errorMsg = null;
        String response = null;

        try {
            response = (String) pjp.proceed();
            return response;
        } catch (Throwable e) {
            success = false;
            errorMsg = e.getMessage();
            throw e;
        } finally {
            long latency = System.currentTimeMillis() - start;
            try {
                auditLogMapper.insert(AiAuditLog.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(userId)
                        .sessionId(sessionId)
                        .agentType(agentType)
                        .userMessage(userMsg)
                        .aiResponse(response != null ? truncate(response, 500) : null)
                        .promptTokens(estimateTokens(userMsg))
                        .completionTokens(estimateTokens(response))
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

    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int cjkCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) cjkCount++;
        }
        int nonCjk = text.length() - cjkCount;
        return (int) (cjkCount * 1.5 + nonCjk * 0.4);
    }
}
