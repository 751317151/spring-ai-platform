package com.huah.ai.platform.agent.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentRequestContextResolverTest {

    private final AgentRequestContextResolver resolver = new AgentRequestContextResolver();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldResolveChineseUserContextFromHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("X-User-Id", "张三");
        request.setAttribute("X-Roles", "ROLE_ADMIN,ROLE_USER");
        request.setAttribute("X-Department", "系统管理");

        AgentRequestContext context = resolver.resolve(request);

        assertEquals("张三", context.getUserId());
        assertEquals("ROLE_ADMIN,ROLE_USER", context.getRoles());
        assertEquals("系统管理", context.getDepartment());
    }

    @Test
    void shouldFallbackToSecurityContextWhenHeadersMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "alice",
                "n/a",
                AuthorityUtils.createAuthorityList("ROLE_RD", "ROLE_USER")
        ));

        AgentRequestContext context = resolver.resolve(request);

        assertEquals("alice", context.getUserId());
        assertEquals("ROLE_RD,ROLE_USER", context.getRoles());
        assertNull(context.getDepartment());
    }
}
