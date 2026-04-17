package com.huah.ai.platform.common.idempotent;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentInterceptor implements HandlerInterceptor {

    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    private static final String DEDUP_KEY_PREFIX = "ai:idempotent:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        Idempotent annotation = handlerMethod.getMethodAnnotation(Idempotent.class);
        if (annotation == null) {
            return true;
        }

        String requestId = request.getHeader(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            return true;
        }

        String dedupKey = DEDUP_KEY_PREFIX + requestId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(dedupKey, "1", Duration.ofSeconds(annotation.ttlSeconds()));

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("[Idempotent] duplicate request blocked: requestId={}, uri={}",
                    requestId, request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":409,\"message\":\"Duplicate request, please wait\"}");
            return false;
        }

        return true;
    }
}
