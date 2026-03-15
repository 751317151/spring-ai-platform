package com.huah.ai.platform.agent.security;

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
 * Agent 访问权限检查器
 * 基于 ai_bot_permissions 表检查用户角色/部门是否有权访问指定 Agent
 * 基于 Redis 检查 Token 每日配额
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAccessChecker {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    private static final String TOKEN_DAILY_KEY = "ai:token:daily:%s:%s";

    /**
     * 检查用户是否有权访问指定 Agent
     *
     * @param agentType  agent 类型 (rd, sales, hr, finance, supply-chain, qc, multi)
     * @param userRoles  用户角色（逗号分隔，如 "ROLE_RD,ROLE_USER"）
     * @param department 用户部门
     * @return null 表示允许; 非 null 为拒绝原因
     */
    public String checkPermission(String agentType, String userRoles, String department) {
        if (userRoles == null) {
            return "用户未分配角色";
        }

        Set<String> roles = Arrays.stream(userRoles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        // ADMIN 拥有所有权限
        if (roles.contains("ROLE_ADMIN")) {
            return null;
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT allowed_roles, allowed_departments, enabled " +
                    "FROM ai_bot_permissions WHERE bot_type = ?",
                    agentType
            );

            if (rows.isEmpty()) {
                // 没有配置权限记录，默认允许
                return null;
            }

            Map<String, Object> perm = rows.get(0);

            // 检查是否启用
            Boolean enabled = (Boolean) perm.get("enabled");
            if (enabled != null && !enabled) {
                return "该 Agent 已被禁用";
            }

            // 检查角色
            String allowedRolesStr = (String) perm.get("allowed_roles");
            if (allowedRolesStr != null && !allowedRolesStr.isBlank()) {
                Set<String> allowedRoles = Arrays.stream(allowedRolesStr.split(","))
                        .map(String::trim)
                        .collect(Collectors.toSet());
                boolean hasRole = roles.stream().anyMatch(allowedRoles::contains);
                if (!hasRole) {
                    return "您的角色无权使用此 Agent（需要: " + allowedRolesStr + "）";
                }
            }

            // 检查部门
            String allowedDepts = (String) perm.get("allowed_departments");
            if (allowedDepts != null && !allowedDepts.isBlank() && department != null) {
                Set<String> deptSet = Arrays.stream(allowedDepts.split(","))
                        .map(String::trim)
                        .collect(Collectors.toSet());
                if (!deptSet.contains(department)) {
                    log.debug("部门检查: user={}, allowed={}", department, allowedDepts);
                    // 部门检查仅作日志记录，不阻止访问（角色匹配即可）
                }
            }

            return null;
        } catch (Exception e) {
            log.warn("权限检查异常，默认允许: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取指定 Agent 的每日 Token 限额
     */
    public int getDailyTokenLimit(String agentType) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT daily_token_limit FROM ai_bot_permissions WHERE bot_type = ? AND enabled = true",
                    agentType
            );
            if (!rows.isEmpty()) {
                Number limit = (Number) rows.get(0).get("daily_token_limit");
                return limit != null ? limit.intValue() : Integer.MAX_VALUE;
            }
        } catch (Exception e) {
            log.warn("获取 Token 限额异常: {}", e.getMessage());
        }
        return Integer.MAX_VALUE;
    }

    /**
     * 检查并消耗 Token 配额
     *
     * @return null 表示允许; 非 null 为拒绝原因
     */
    public String checkAndConsumeTokens(String userId, String agentType, int tokens) {
        int limit = getDailyTokenLimit(agentType);
        if (limit == Integer.MAX_VALUE) {
            return null;
        }

        String today = LocalDate.now().toString();
        String key = String.format(TOKEN_DAILY_KEY, userId, today);

        Long current = redisTemplate.opsForValue().increment(key, tokens);
        redisTemplate.expire(key, 2, TimeUnit.DAYS);

        if (current != null && current > limit) {
            // 回滚
            redisTemplate.opsForValue().decrement(key, tokens);
            log.warn("Token 超限: userId={}, agent={}, usage={}, limit={}",
                    userId, agentType, current, limit);
            return "今日 Token 配额已用完（限额: " + limit + "）";
        }

        return null;
    }

    /**
     * 记录实际 Token 消耗（在 AI 调用完成后调用，补正预扣量）
     */
    public void recordActualTokens(String userId, int actualTokens, int preDeducted) {
        if (actualTokens == preDeducted) return;
        String today = LocalDate.now().toString();
        String key = String.format(TOKEN_DAILY_KEY, userId, today);
        int diff = actualTokens - preDeducted;
        if (diff > 0) {
            redisTemplate.opsForValue().increment(key, diff);
        } else {
            redisTemplate.opsForValue().decrement(key, -diff);
        }
    }
}
