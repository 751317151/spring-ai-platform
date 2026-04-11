package com.huah.ai.platform.agent.controller;

import com.huah.ai.platform.agent.security.AgentAccessChecker;
import com.huah.ai.platform.common.web.RequestOrigin;
import com.huah.ai.platform.common.web.RequestOriginResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AgentControllerSupport {

    private final AgentRequestContextResolver requestContextResolver;
    private final RequestOriginResolver requestOriginResolver;

    AgentRequestContext resolveContext(HttpServletRequest request) {
        return requestContextResolver.resolve(request);
    }

    RequestOrigin resolveOrigin(HttpServletRequest request) {
        return requestOriginResolver.resolve(request);
    }

    String currentUserId(HttpServletRequest request) {
        return resolveContext(request).getUserId();
    }

    String checkAgentAccess(String agentType, HttpServletRequest request, AgentAccessChecker accessChecker) {
        AgentRequestContext context = resolveContext(request);
        return accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment());
    }

    String checkAgentAccess(String agentType, HttpServletRequest request, AgentAccessChecker accessChecker, String requiredOperation) {
        AgentRequestContext context = resolveContext(request);
        return accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment(), requiredOperation);
    }

    boolean isAdmin(HttpServletRequest request) {
        AgentRequestContext context = resolveContext(request);
        if (context.getRoles() == null || context.getRoles().isBlank()) {
            return false;
        }
        for (String role : context.getRoles().split(",")) {
            if ("ROLE_ADMIN".equals(role.trim())) {
                return true;
            }
        }
        return false;
    }

    boolean ownsSession(String userId, String agentType, String sessionId) {
        return sessionId != null && sessionId.startsWith(buildSessionPrefix(userId, agentType));
    }

    String buildSessionPrefix(String userId, String agentType) {
        return URLEncoder.encode(userId, StandardCharsets.UTF_8) + "-" + agentType + "-";
    }

    int safeLimit(Integer limit, int defaultValue, int min, int max) {
        int value = limit == null ? defaultValue : limit;
        return Math.max(min, Math.min(value, max));
    }

    String truncate(String text, int maxLen) {
        if (text == null) {
            return "null";
        }
        return text.length() <= maxLen
                ? text
                : text.substring(0, maxLen) + "...(truncated, total=" + text.length() + ")";
    }
}
