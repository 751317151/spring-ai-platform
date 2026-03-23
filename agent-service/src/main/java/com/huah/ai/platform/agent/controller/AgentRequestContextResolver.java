package com.huah.ai.platform.agent.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AgentRequestContextResolver {

    public AgentRequestContext resolve(HttpServletRequest request) {
        return AgentRequestContext.builder()
                .userId(resolveUserId(request))
                .roles(resolveRoles(request))
                .department(resolveDepartment(request))
                .build();
    }

    private String resolveUserId(HttpServletRequest request) {
        Object attr = request.getAttribute("X-User-Id");
        if (attr != null) {
            return attr.toString();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            return authentication.getPrincipal().toString();
        }
        return "anonymous";
    }

    private String resolveRoles(HttpServletRequest request) {
        Object attr = request.getAttribute("X-Roles");
        if (attr != null) {
            return attr.toString();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return null;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
    }

    private String resolveDepartment(HttpServletRequest request) {
        Object attr = request.getAttribute("X-Department");
        return attr != null ? attr.toString() : null;
    }
}
