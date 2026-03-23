package com.huah.ai.platform.agent.security;

import com.huah.ai.platform.agent.metrics.AiMetricsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentAccessCheckerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AiMetricsCollector metricsCollector;

    private AgentAccessChecker checker;

    @BeforeEach
    void setUp() {
        checker = new AgentAccessChecker(jdbcTemplate, redisTemplate, metricsCollector);
    }

    @Test
    void checkPermissionAllowsMatchingRoleAndDepartment() {
        when(jdbcTemplate.queryForList(
                "SELECT allowed_roles, allowed_departments, enabled FROM ai_bot_permissions WHERE bot_type = ?",
                "rd"
        )).thenReturn(List.of(Map.of(
                "allowed_roles", "ROLE_RD,ROLE_ADMIN",
                "allowed_departments", "研发中心",
                "enabled", true
        )));

        String result = checker.checkPermission("rd", "ROLE_RD,ROLE_USER", "研发中心");

        assertNull(result);
    }

    @Test
    void checkPermissionRejectsRoleMismatch() {
        when(jdbcTemplate.queryForList(
                "SELECT allowed_roles, allowed_departments, enabled FROM ai_bot_permissions WHERE bot_type = ?",
                "finance"
        )).thenReturn(List.of(Map.of(
                "allowed_roles", "ROLE_FINANCE,ROLE_ADMIN",
                "allowed_departments", "财务部",
                "enabled", true
        )));

        String result = checker.checkPermission("finance", "ROLE_USER", "财务部");

        assertEquals("您的角色无权使用该 Agent，需要角色: ROLE_FINANCE,ROLE_ADMIN", result);
    }

    @Test
    void checkPermissionRejectsMissingPermissionRecord() {
        when(jdbcTemplate.queryForList(
                "SELECT allowed_roles, allowed_departments, enabled FROM ai_bot_permissions WHERE bot_type = ?",
                "hr"
        )).thenReturn(List.of());

        String result = checker.checkPermission("hr", "ROLE_HR,ROLE_USER", "人力资源部");

        assertEquals("未找到该 Agent 的权限配置", result);
    }

    @Test
    void checkPermissionRejectsWhenPermissionQueryFails() {
        when(jdbcTemplate.queryForList(
                "SELECT allowed_roles, allowed_departments, enabled FROM ai_bot_permissions WHERE bot_type = ?",
                "sales"
        )).thenThrow(new RuntimeException("db down"));

        String result = checker.checkPermission("sales", "ROLE_SALES,ROLE_USER", "销售部");

        assertEquals("权限校验失败，请稍后重试", result);
    }

    @Test
    void checkAndConsumeTokensRollsBackWhenLimitExceeded() {
        when(jdbcTemplate.queryForList(
                "SELECT daily_token_limit FROM ai_bot_permissions WHERE bot_type = ? AND enabled = true",
                "rd"
        )).thenReturn(List.of(Map.of("daily_token_limit", 100)));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString(), eq(120L))).thenReturn(120L);

        String result = checker.checkAndConsumeTokens("user-1", "rd", 120);

        assertEquals("今日 Token 配额已用完，限额: 100", result);
        verify(valueOperations).decrement(anyString(), eq(120L));
        verify(redisTemplate).expire(anyString(), eq(2L), eq(TimeUnit.DAYS));
    }
}
