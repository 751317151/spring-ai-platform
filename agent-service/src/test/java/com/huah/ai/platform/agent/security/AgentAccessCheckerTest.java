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
            "SELECT d.enabled, d.daily_token_limit, "
                    + "COALESCE(STRING_AGG(r.role_name, ',' ORDER BY r.role_name), '') AS allowed_roles "
                    + "FROM ai_agent_definitions d "
                    + "LEFT JOIN ai_agent_roles ar ON ar.agent_code = d.agent_code "
                    + "LEFT JOIN ai_roles r ON r.id = ar.role_id "
                    + "WHERE d.agent_code = ? "
                    + "GROUP BY d.agent_code, d.enabled, d.daily_token_limit";

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
    void checkPermissionAllowsMatchingRole() {
        when(jdbcTemplate.queryForList(QUERY_PERMISSION, "rd")).thenReturn(List.of(Map.of(
                "allowed_roles", "ROLE_RD,ROLE_ADMIN",
                "daily_token_limit", 100000,
                "enabled", true
        )));

        String result = checker.checkPermission("rd", "ROLE_RD,ROLE_USER", "R&D", "CHAT");

        assertNull(result);
    }

    @Test
    void checkPermissionRejectsRoleMismatch() {
        when(jdbcTemplate.queryForList(QUERY_PERMISSION, "finance")).thenReturn(List.of(Map.of(
                "allowed_roles", "ROLE_FINANCE,ROLE_ADMIN",
                "daily_token_limit", 100000,
                "enabled", true
        )));

        String result = checker.checkPermission("finance", "ROLE_USER", "Finance", "CHAT");

        assertEquals("您的角色无权使用该 Agent，需要角色: ROLE_FINANCE,ROLE_ADMIN", result);
    }

    @Test
    void checkPermissionRejectsMissingPermissionRecord() {
        when(jdbcTemplate.queryForList(QUERY_PERMISSION, "hr")).thenReturn(List.of());

        String result = checker.checkPermission("hr", "ROLE_HR,ROLE_USER", "HR", "CHAT");

        assertEquals("未找到该 Agent 的权限配置", result);
    }

    @Test
    void checkPermissionRejectsWhenPermissionQueryFails() {
        when(jdbcTemplate.queryForList(QUERY_PERMISSION, "sales")).thenThrow(new RuntimeException("db down"));

        String result = checker.checkPermission("sales", "ROLE_SALES,ROLE_USER", "Sales", "CHAT");

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
                "SELECT daily_token_limit FROM ai_agent_definitions WHERE agent_code = ? AND enabled = true",
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
