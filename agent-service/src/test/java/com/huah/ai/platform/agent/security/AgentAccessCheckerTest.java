package com.huah.ai.platform.agent.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huah.ai.platform.agent.metrics.AiMetricsCollector;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AgentAccessCheckerTest {

    private static final String QUERY_PERMISSION =
            "SELECT allowed_roles, allowed_departments, allowed_operations, enabled "
                    + "FROM ai_bot_permissions WHERE bot_type = ?";

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
        when(jdbcTemplate.queryForList(QUERY_PERMISSION, "rd")).thenReturn(List.of(Map.of(
                "allowed_roles", "ROLE_RD,ROLE_ADMIN",
                "allowed_departments", "研发中心",
                "allowed_operations", "CHAT,READ",
                "enabled", true
        )));

        String result = checker.checkPermission("rd", "ROLE_RD,ROLE_USER", "研发中心", "CHAT");

        assertNull(result);
    }

    @Test
    void checkPermissionRejectsRoleMismatch() {
        when(jdbcTemplate.queryForList(QUERY_PERMISSION, "finance")).thenReturn(List.of(Map.of(
                "allowed_roles", "ROLE_FINANCE,ROLE_ADMIN",
                "allowed_departments", "财务部",
                "allowed_operations", "CHAT",
                "enabled", true
        )));

        String result = checker.checkPermission("finance", "ROLE_USER", "财务部");

        assertEquals("您的角色无权使用该 Agent，需要角色: ROLE_FINANCE,ROLE_ADMIN", result);
    }

    @Test
    void checkPermissionRejectsOperationMismatch() {
        when(jdbcTemplate.queryForList(QUERY_PERMISSION, "search")).thenReturn(List.of(Map.of(
                "allowed_roles", "ROLE_RD,ROLE_ADMIN",
                "allowed_departments", "研发中心",
                "allowed_operations", "READ",
                "enabled", true
        )));

        String result = checker.checkPermission("search", "ROLE_RD", "研发中心", "WRITE");

        assertEquals("当前权限规则不允许该操作，需要操作权限: WRITE", result);
    }

    @Test
    void checkPermissionRejectsMissingPermissionRecord() {
        when(jdbcTemplate.queryForList(QUERY_PERMISSION, "hr")).thenReturn(List.of());

        String result = checker.checkPermission("hr", "ROLE_HR,ROLE_USER", "人力资源部");

        assertEquals("未找到该 Agent 的权限配置", result);
    }

    @Test
    void checkPermissionRejectsWhenPermissionQueryFails() {
        when(jdbcTemplate.queryForList(QUERY_PERMISSION, "sales")).thenThrow(new RuntimeException("db down"));

        String result = checker.checkPermission("sales", "ROLE_SALES,ROLE_USER", "销售部");

        assertEquals("权限校验失败，请稍后重试", result);
    }

    @Test
    void checkAndConsumeTokensRollsBackWhenLimitExceeded() {
        when(jdbcTemplate.queryForList(
                "SELECT daily_token_limit FROM ai_user_token_limits "
                        + "WHERE user_id = ? AND bot_type = ? AND enabled = true LIMIT 1",
                "user-1",
                "rd"
        )).thenReturn(List.of());
        when(jdbcTemplate.queryForList(
                "SELECT daily_token_limit FROM ai_user_token_limits "
                        + "WHERE user_id = ? AND bot_type IS NULL AND enabled = true LIMIT 1",
                "user-1"
        )).thenReturn(List.of());
        when(jdbcTemplate.queryForList(
                "SELECT r.role_name, rtl.daily_token_limit "
                        + "FROM ai_role_token_limits rtl "
                        + "INNER JOIN ai_roles r ON r.id = rtl.role_id "
                        + "INNER JOIN ai_user_roles ur ON ur.role_id = rtl.role_id "
                        + "WHERE ur.user_id = ? AND rtl.bot_type = ? AND rtl.enabled = true",
                "user-1",
                "rd"
        )).thenReturn(List.of());
        when(jdbcTemplate.queryForList(
                "SELECT r.role_name, rtl.daily_token_limit "
                        + "FROM ai_role_token_limits rtl "
                        + "INNER JOIN ai_roles r ON r.id = rtl.role_id "
                        + "INNER JOIN ai_user_roles ur ON ur.role_id = rtl.role_id "
                        + "WHERE ur.user_id = ? AND rtl.bot_type IS NULL AND rtl.enabled = true",
                "user-1"
        )).thenReturn(List.of());
        when(jdbcTemplate.queryForList(
                "SELECT daily_token_limit FROM ai_bot_permissions WHERE bot_type = ? AND enabled = true",
                "rd"
        )).thenReturn(List.of(Map.of("daily_token_limit", 100)));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString(), eq(120L))).thenReturn(120L);

        String result = checker.checkAndConsumeTokens("user-1", "rd", 120);

        assertEquals("今日 Token 配额已用完，限额: 100（助手默认配额）", result);
        verify(valueOperations, times(2)).decrement(anyString(), eq(120L));
        verify(redisTemplate, times(2)).expire(anyString(), eq(2L), eq(TimeUnit.DAYS));
    }
}
