package com.huah.ai.platform.agent.security;

import com.huah.ai.platform.agent.metrics.AiMetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Agent access and token quota checker.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAccessChecker {

    private static final String TOKEN_DAILY_KEY = "ai:token:daily:%s:%s";
    private static final String QUERY_PERMISSION =
            "SELECT allowed_roles, allowed_departments, enabled FROM ai_bot_permissions WHERE bot_type = ?";
    private static final String QUERY_DAILY_LIMIT =
            "SELECT daily_token_limit FROM ai_bot_permissions WHERE bot_type = ? AND enabled = true";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final AiMetricsCollector metricsCollector;

    public String checkPermission(String agentType, String userRoles, String department) {
        if (userRoles == null || userRoles.isBlank()) {
            return "\u5f53\u524d\u7528\u6237\u672a\u5206\u914d\u89d2\u8272";
        }

        Set<String> roles = Arrays.stream(userRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toSet());

        if (roles.contains("ROLE_ADMIN")) {
            return null;
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(QUERY_PERMISSION, agentType);
            if (rows.isEmpty()) {
                log.warn("Agent permission config missing, deny access: agentType={}", agentType);
                return "\u672a\u627e\u5230\u8be5 Agent \u7684\u6743\u9650\u914d\u7f6e";
            }

            Map<String, Object> permission = rows.get(0);
            Boolean enabled = (Boolean) permission.get("enabled");
            if (enabled != null && !enabled) {
                return "\u8be5 Agent \u5df2\u88ab\u7981\u7528";
            }

            String allowedRolesStr = (String) permission.get("allowed_roles");
            if (allowedRolesStr != null && !allowedRolesStr.isBlank()) {
                Set<String> allowedRoles = Arrays.stream(allowedRolesStr.split(","))
                        .map(String::trim)
                        .filter(role -> !role.isEmpty())
                        .collect(Collectors.toSet());
                boolean hasRole = roles.stream().anyMatch(allowedRoles::contains);
                if (!hasRole) {
                    return "\u60a8\u7684\u89d2\u8272\u65e0\u6743\u4f7f\u7528\u8be5 Agent\uff0c\u9700\u8981\u89d2\u8272: "
                            + allowedRolesStr;
                }
            }

            String allowedDepartments = (String) permission.get("allowed_departments");
            if (allowedDepartments != null && !allowedDepartments.isBlank()) {
                if (department == null || department.isBlank()) {
                    return "\u5f53\u524d\u7528\u6237\u7f3a\u5c11\u90e8\u95e8\u4fe1\u606f\uff0c\u65e0\u6cd5\u8bbf\u95ee\u8be5 Agent";
                }

                Set<String> departmentSet = Arrays.stream(allowedDepartments.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .collect(Collectors.toSet());
                if (!departmentSet.contains(department.trim())) {
                    return "\u60a8\u6240\u5728\u90e8\u95e8\u65e0\u6743\u4f7f\u7528\u8be5 Agent\uff0c\u9700\u8981\u90e8\u95e8: "
                            + allowedDepartments;
                }
            }

            return null;
        } catch (Exception e) {
            metricsCollector.recordDependencyFailure("database", "permission-query");
            log.warn("Agent permission validation failed, deny access: agentType={}, error={}", agentType, e.getMessage());
            return "\u6743\u9650\u6821\u9a8c\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5";
        }
    }

    public int getDailyTokenLimit(String agentType) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(QUERY_DAILY_LIMIT, agentType);
            if (!rows.isEmpty()) {
                Number limit = (Number) rows.get(0).get("daily_token_limit");
                return limit != null ? limit.intValue() : Integer.MAX_VALUE;
            }
        } catch (Exception e) {
            metricsCollector.recordDependencyFailure("database", "token-limit-query");
            log.warn("Failed to load token limit: agentType={}, error={}", agentType, e.getMessage());
        }
        return Integer.MAX_VALUE;
    }

    public String checkAndConsumeTokens(String userId, String agentType, int tokens) {
        int limit = getDailyTokenLimit(agentType);
        if (limit == Integer.MAX_VALUE) {
            return null;
        }

        String today = LocalDate.now().toString();
        String key = String.format(TOKEN_DAILY_KEY, userId, today);

        try {
            Long current = redisTemplate.opsForValue().increment(key, tokens);
            redisTemplate.expire(key, 2, TimeUnit.DAYS);

            if (current != null && current > limit) {
                redisTemplate.opsForValue().decrement(key, tokens);
                metricsCollector.recordTokenLimitExceeded(agentType);
                log.warn("Token limit exceeded: userId={}, agentType={}, usage={}, limit={}", userId, agentType, current, limit);
                return "\u4eca\u65e5 Token \u914d\u989d\u5df2\u7528\u5b8c\uff0c\u9650\u989d: " + limit;
            }
        } catch (Exception e) {
            metricsCollector.recordDependencyFailure("redis", "token-quota-check");
            log.warn("Redis token quota validation failed: userId={}, agentType={}, error={}", userId, agentType, e.getMessage());
            return "Token \u914d\u989d\u6821\u9a8c\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5";
        }

        return null;
    }

    public void recordActualTokens(String userId, int actualTokens, int preDeducted) {
        if (actualTokens == preDeducted) {
            return;
        }
        String today = LocalDate.now().toString();
        String key = String.format(TOKEN_DAILY_KEY, userId, today);
        int diff = actualTokens - preDeducted;
        try {
            if (diff > 0) {
                redisTemplate.opsForValue().increment(key, diff);
            } else {
                redisTemplate.opsForValue().decrement(key, -diff);
            }
        } catch (Exception e) {
            metricsCollector.recordDependencyFailure("redis", "token-quota-adjust");
            log.warn("Redis token quota adjustment failed: userId={}, error={}", userId, e.getMessage());
        }
    }
}
