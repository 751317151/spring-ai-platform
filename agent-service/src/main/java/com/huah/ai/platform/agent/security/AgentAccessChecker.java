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
            return "The current user has no assigned roles";
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
                return "No permission configuration was found for the requested agent";
            }

            Map<String, Object> permission = rows.get(0);
            Boolean enabled = (Boolean) permission.get("enabled");
            if (enabled != null && !enabled) {
                return "The requested agent is disabled";
            }

            String allowedRolesStr = (String) permission.get("allowed_roles");
            if (allowedRolesStr != null && !allowedRolesStr.isBlank()) {
                Set<String> allowedRoles = Arrays.stream(allowedRolesStr.split(","))
                        .map(String::trim)
                        .filter(role -> !role.isEmpty())
                        .collect(Collectors.toSet());
                boolean hasRole = roles.stream().anyMatch(allowedRoles::contains);
                if (!hasRole) {
                    return "The current user role is not allowed to use this agent. Allowed roles: " + allowedRolesStr;
                }
            }

            String allowedDepartments = (String) permission.get("allowed_departments");
            if (allowedDepartments != null && !allowedDepartments.isBlank()) {
                if (department == null || department.isBlank()) {
                    return "The current user does not have department information and cannot access this agent";
                }

                Set<String> departmentSet = Arrays.stream(allowedDepartments.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .collect(Collectors.toSet());
                if (!departmentSet.contains(department.trim())) {
                    return "The current user department is not allowed to use this agent. Allowed departments: " + allowedDepartments;
                }
            }

            return null;
        } catch (Exception e) {
            metricsCollector.recordDependencyFailure("database", "permission-query");
            log.warn("Agent permission validation failed, deny access: agentType={}, error={}", agentType, e.getMessage());
            return "Permission validation failed, please try again later";
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
                return "The daily token quota has been exhausted. Limit: " + limit;
            }
        } catch (Exception e) {
            metricsCollector.recordDependencyFailure("redis", "token-quota-check");
            log.warn("Redis token quota validation failed: userId={}, agentType={}, error={}", userId, agentType, e.getMessage());
            return "Token quota validation failed, please try again later";
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
