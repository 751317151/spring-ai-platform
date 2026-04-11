package com.huah.ai.platform.agent.security;

import com.huah.ai.platform.agent.metrics.AiMetricsCollector;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Agent access and token quota checker.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAccessChecker {

    private static final String TOKEN_DAILY_TOTAL_KEY = "ai:token:daily:total:%s:%s";
    private static final String TOKEN_DAILY_AGENT_KEY = "ai:token:daily:agent:%s:%s:%s";
    private static final String QUERY_PERMISSION =
            "SELECT d.enabled, d.daily_token_limit, "
                    + "COALESCE(STRING_AGG(r.role_name, ',' ORDER BY r.role_name), '') AS allowed_roles "
                    + "FROM ai_agent_definitions d "
                    + "LEFT JOIN ai_agent_roles ar ON ar.agent_code = d.agent_code "
                    + "LEFT JOIN ai_roles r ON r.id = ar.role_id "
                    + "WHERE d.agent_code = ? "
                    + "GROUP BY d.agent_code, d.enabled, d.daily_token_limit";
    private static final String QUERY_BOT_DAILY_LIMIT =
            "SELECT daily_token_limit FROM ai_agent_definitions WHERE agent_code = ? AND enabled = true";
    private static final String QUERY_USER_AGENT_DAILY_LIMIT =
            "SELECT daily_token_limit FROM ai_user_token_limits "
                    + "WHERE user_id = ? AND bot_type = ? AND enabled = true LIMIT 1";
    private static final String QUERY_USER_TOTAL_DAILY_LIMIT =
            "SELECT daily_token_limit FROM ai_user_token_limits "
                    + "WHERE user_id = ? AND bot_type IS NULL AND enabled = true LIMIT 1";
    private static final String QUERY_ROLE_AGENT_DAILY_LIMIT =
            "SELECT r.role_name, rtl.daily_token_limit "
                    + "FROM ai_role_token_limits rtl "
                    + "INNER JOIN ai_roles r ON r.id = rtl.role_id "
                    + "INNER JOIN ai_user_roles ur ON ur.role_id = rtl.role_id "
                    + "WHERE ur.user_id = ? AND rtl.bot_type = ? AND rtl.enabled = true";
    private static final String QUERY_ROLE_TOTAL_DAILY_LIMIT =
            "SELECT r.role_name, rtl.daily_token_limit "
                    + "FROM ai_role_token_limits rtl "
                    + "INNER JOIN ai_roles r ON r.id = rtl.role_id "
                    + "INNER JOIN ai_user_roles ur ON ur.role_id = rtl.role_id "
                    + "WHERE ur.user_id = ? AND rtl.bot_type IS NULL AND rtl.enabled = true";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final AiMetricsCollector metricsCollector;

    public String checkPermission(String agentType, String userRoles, String department) {
        return checkPermission(agentType, userRoles, department, null);
    }

    public String checkPermission(String agentType, String userRoles, String department, String requiredOperation) {
        if (userRoles == null || userRoles.isBlank()) {
            return "当前用户未分配角色";
        }

        Set<String> roles = parseValues(userRoles);
        if (roles.contains("ROLE_ADMIN")) {
            return null;
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(QUERY_PERMISSION, agentType);
            if (rows.isEmpty()) {
                log.warn("Agent permission config missing, deny access: agentType={}", agentType);
                return "未找到该 Agent 的权限配置";
            }

            Map<String, Object> permission = rows.get(0);
            Boolean enabled = (Boolean) permission.get("enabled");
            if (enabled != null && !enabled) {
                return "该 Agent 已被禁用";
            }

            String allowedRolesStr = (String) permission.get("allowed_roles");
            if (allowedRolesStr != null && !allowedRolesStr.isBlank()) {
                Set<String> allowedRoles = parseValues(allowedRolesStr);
                boolean hasRole = roles.stream().anyMatch(allowedRoles::contains);
                if (!hasRole) {
                    return "您的角色无权使用该 Agent，需要角色: " + allowedRolesStr;
                }
            }

            return null;
        } catch (Exception e) {
            metricsCollector.recordDependencyFailure("database", "permission-query");
            log.warn("Agent permission validation failed, deny access: agentType={}, error={}", agentType, e.getMessage());
            return "权限校验失败，请稍后重试";
        }
    }

    public int getDailyTokenLimit(String agentType) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(QUERY_BOT_DAILY_LIMIT, agentType);
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
        TokenQuotaLimit quotaLimit = resolveEffectiveQuota(userId, agentType);
        if (quotaLimit.limit() == Integer.MAX_VALUE) {
            return null;
        }

        String today = LocalDate.now().toString();
        String totalKey = String.format(TOKEN_DAILY_TOTAL_KEY, userId, today);
        String agentKey = String.format(TOKEN_DAILY_AGENT_KEY, userId, agentType, today);

        try {
            Long currentTotal = redisTemplate.opsForValue().increment(totalKey, tokens);
            Long currentAgent = redisTemplate.opsForValue().increment(agentKey, tokens);
            redisTemplate.expire(totalKey, 2, TimeUnit.DAYS);
            redisTemplate.expire(agentKey, 2, TimeUnit.DAYS);

            long currentUsage = quotaLimit.agentScoped()
                    ? valueOrZero(currentAgent)
                    : valueOrZero(currentTotal);
            if (currentUsage > quotaLimit.limit()) {
                redisTemplate.opsForValue().decrement(totalKey, tokens);
                redisTemplate.opsForValue().decrement(agentKey, tokens);
                metricsCollector.recordTokenLimitExceeded(agentType);
                log.warn("Token limit exceeded: userId={}, agentType={}, usage={}, limit={}, source={}",
                        userId, agentType, currentUsage, quotaLimit.limit(), quotaLimit.source());
                return "今日 Token 配额已用完，限额: " + quotaLimit.limit() + "（" + quotaLimit.description() + "）";
            }
        } catch (Exception e) {
            metricsCollector.recordDependencyFailure("redis", "token-quota-check");
            log.warn("Redis token quota validation failed: userId={}, agentType={}, error={}", userId, agentType, e.getMessage());
            return "Token 配额校验失败，请稍后重试";
        }

        return null;
    }

    public void recordActualTokens(String userId, String agentType, int actualTokens, int preDeducted) {
        if (actualTokens == preDeducted) {
            return;
        }
        String today = LocalDate.now().toString();
        String totalKey = String.format(TOKEN_DAILY_TOTAL_KEY, userId, today);
        String agentKey = String.format(TOKEN_DAILY_AGENT_KEY, userId, agentType, today);
        int diff = actualTokens - preDeducted;
        try {
            if (diff > 0) {
                redisTemplate.opsForValue().increment(totalKey, diff);
                redisTemplate.opsForValue().increment(agentKey, diff);
            } else {
                redisTemplate.opsForValue().decrement(totalKey, -diff);
                redisTemplate.opsForValue().decrement(agentKey, -diff);
            }
        } catch (Exception e) {
            metricsCollector.recordDependencyFailure("redis", "token-quota-adjust");
            log.warn("Redis token quota adjustment failed: userId={}, error={}", userId, e.getMessage());
        }
    }

    private TokenQuotaLimit resolveEffectiveQuota(String userId, String agentType) {
        try {
            Integer userAgentLimit = querySingleLimit(QUERY_USER_AGENT_DAILY_LIMIT, userId, agentType);
            if (userAgentLimit != null) {
                return new TokenQuotaLimit(userAgentLimit, true, "user-agent", "用户助手专属配额");
            }

            Integer userTotalLimit = querySingleLimit(QUERY_USER_TOTAL_DAILY_LIMIT, userId);
            if (userTotalLimit != null) {
                return new TokenQuotaLimit(userTotalLimit, false, "user-total", "用户总配额");
            }

            TokenQuotaLimit roleAgentLimit = queryRoleBasedLimit(QUERY_ROLE_AGENT_DAILY_LIMIT, userId, agentType, true, "角色助手配额");
            if (roleAgentLimit != null) {
                return roleAgentLimit;
            }

            TokenQuotaLimit roleTotalLimit = queryRoleBasedLimit(QUERY_ROLE_TOTAL_DAILY_LIMIT, userId, null, false, "角色总配额");
            if (roleTotalLimit != null) {
                return roleTotalLimit;
            }

            int botDefaultLimit = getDailyTokenLimit(agentType);
            if (botDefaultLimit != Integer.MAX_VALUE) {
                return new TokenQuotaLimit(botDefaultLimit, true, "bot-default", "助手默认配额");
            }
        } catch (Exception e) {
            metricsCollector.recordDependencyFailure("database", "token-limit-query");
            log.warn("Failed to resolve token quota: userId={}, agentType={}, error={}", userId, agentType, e.getMessage());
        }
        return new TokenQuotaLimit(Integer.MAX_VALUE, true, "unlimited", "未设置配额");
    }

    private Integer querySingleLimit(String sql, Object... params) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        if (rows.isEmpty()) {
            return null;
        }
        Number limit = (Number) rows.get(0).get("daily_token_limit");
        return limit == null ? null : limit.intValue();
    }

    private TokenQuotaLimit queryRoleBasedLimit(
            String sql,
            String userId,
            String agentType,
            boolean agentScoped,
            String descriptionPrefix) {
        List<Map<String, Object>> rows = agentType == null
                ? jdbcTemplate.queryForList(sql, userId)
                : jdbcTemplate.queryForList(sql, userId, agentType);
        if (rows.isEmpty()) {
            return null;
        }

        int limit = rows.stream()
                .map(row -> (Number) row.get("daily_token_limit"))
                .filter(Objects::nonNull)
                .mapToInt(Number::intValue)
                .min()
                .orElse(Integer.MAX_VALUE);
        String roleNames = rows.stream()
                .map(row -> String.valueOf(row.get("role_name")))
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
        return new TokenQuotaLimit(limit, agentScoped, "role", descriptionPrefix + "，取最小角色额度: " + roleNames);
    }

    private Set<String> parseValues(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(item -> item.toUpperCase().equals(item) ? item : item)
                .collect(Collectors.toSet());
    }

    private boolean isOperationAllowed(String allowedOperations, String requiredOperation) {
        return true;
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private record TokenQuotaLimit(int limit, boolean agentScoped, String source, String description) {
    }
}
