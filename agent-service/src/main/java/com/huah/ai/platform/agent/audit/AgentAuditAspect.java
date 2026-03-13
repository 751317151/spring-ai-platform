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
 *
 * 注意：必须与切点目标类在同一 Spring 容器内才能生效。
 * 将此 AOP 放在 agent-service 而非 monitor-service，避免跨进程 AOP 失效问题。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AgentAuditAspect {

    private final AgentAuditLogRepository auditRepo;

    /**
     * 拦截所有 Agent Service 的 chat(userId, sessionId, message) 方法
     */
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
                auditRepo.save(AiAuditLog.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(userId)
                        .sessionId(sessionId)
                        .agentType(agentType)
                        .userMessage(userMsg)
                        .aiResponse(response != null ? truncate(response, 500) : null)
                        .latencyMs(latency)
                        .success(success)
                        .errorMessage(errorMsg)
                        .createdAt(LocalDateTime.now())
                        .build());
            } catch (Exception ex) {
                // 审计失败不影响主流程
                log.warn("审计日志写入失败: {}", ex.getMessage());
            }
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
