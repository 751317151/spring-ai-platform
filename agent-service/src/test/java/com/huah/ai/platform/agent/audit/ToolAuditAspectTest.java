package com.huah.ai.platform.agent.audit;

import com.huah.ai.platform.agent.config.ToolsProperties;
import com.huah.ai.platform.agent.security.ToolAccessDeniedException;
import com.huah.ai.platform.agent.support.AgentTestFixtures;
import com.huah.ai.platform.agent.security.ToolSecurityService;
import com.huah.ai.platform.common.util.SnowflakeIdGenerator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.annotation.Tool;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolAuditAspectTest {

    private final AiToolAuditLogMapper mapper = mock(AiToolAuditLogMapper.class);
    private final ToolsProperties toolsProperties = AgentTestFixtures.toolsProperties();
    private final ToolSecurityService toolSecurityService = AgentTestFixtures.toolSecurityService(toolsProperties);
    private final SnowflakeIdGenerator snowflakeIdGenerator = new SnowflakeIdGenerator();
    private final ToolAuditAspect aspect = new ToolAuditAspect(
            mapper,
            AgentTestFixtures.objectMapper(),
            toolSecurityService,
            snowflakeIdGenerator
    );

    @AfterEach
    void tearDown() {
        ToolExecutionContext.clear();
    }

    @Test
    void shouldPersistToolAuditLogWithContext() throws Throwable {
        DummyTools target = new DummyTools();
        Method method = DummyTools.class.getMethod("echo", String.class);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.getArgs()).thenReturn(new Object[]{"hello"});
        when(pjp.getTarget()).thenReturn(target);
        when(pjp.proceed()).thenReturn("world");

        ToolExecutionContext.set("u-1", "s-1", "rd");
        Object result = aspect.auditTool(pjp, method.getAnnotation(Tool.class));

        assertEquals("world", result);
        ArgumentCaptor<AiToolAuditLog> captor = ArgumentCaptor.forClass(AiToolAuditLog.class);
        verify(mapper).insert(captor.capture());
        AiToolAuditLog log = captor.getValue();
        assertEquals("u-1", log.getUserId());
        assertEquals("s-1", log.getSessionId());
        assertEquals("rd", log.getAgentType());
        assertEquals("echo", log.getToolName());
        assertTrue(Boolean.TRUE.equals(log.getSuccess()));
        assertTrue(log.getInputSummary().contains("hello"));
        assertTrue(log.getOutputSummary().contains("world"));
    }

    @Test
    void shouldDenyToolWhenAgentIsNotAllowed() throws Throwable {
        DummyTools target = new DummyTools();
        Method method = DummyTools.class.getMethod("echo", String.class);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(pjp.getArgs()).thenReturn(new Object[]{"hello"});
        when(pjp.getTarget()).thenReturn(target);

        toolsProperties.getSecurity().setEnabled(true);
        toolsProperties.getSecurity().getAgentToolAllowlist().put("rd", java.util.List.of("otherTool"));
        ToolExecutionContext.set("u-1", "s-1", "rd");

        ToolAccessDeniedException ex = assertThrows(ToolAccessDeniedException.class,
                () -> aspect.auditTool(pjp, method.getAnnotation(Tool.class)));
        assertEquals("TOOL_DENIED", ex.getReasonCode());
        assertEquals("tool:echo", ex.getResource());
        verify(pjp, never()).proceed();
    }

    static class DummyTools {
        @Tool(description = "echo")
        public String echo(String text) {
            return text;
        }
    }
}
