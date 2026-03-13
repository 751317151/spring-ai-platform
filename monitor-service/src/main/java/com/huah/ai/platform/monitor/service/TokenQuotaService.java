package com.huah.ai.platform.monitor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * Token 配额限制服务
 * 按用户、按 Bot、按日统计 Token 消耗，超限则拒绝请求
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenQuotaService {

    private final StringRedisTemplate redisTemplate;

    private static final String DAILY_KEY_PATTERN = "ai:token:daily:%s:%s";  // userId:date
    private static final String BOT_DAILY_KEY_PATTERN = "ai:token:bot:%s:%s:%s"; // botType:userId:date

    /**
     * 检查是否超出 Token 配额
     */
    public boolean checkAndConsumeTokens(String userId, String botType, int tokens, int dailyLimit) {
        String today = LocalDate.now().toString();
        String userDailyKey = String.format(DAILY_KEY_PATTERN, userId, today);
        String botDailyKey = String.format(BOT_DAILY_KEY_PATTERN, botType, userId, today);

        // 原子性自增并检查
        Long currentUsage = redisTemplate.opsForValue().increment(userDailyKey, tokens);
        redisTemplate.expire(userDailyKey, 2, TimeUnit.DAYS);
        redisTemplate.opsForValue().increment(botDailyKey, tokens);
        redisTemplate.expire(botDailyKey, 2, TimeUnit.DAYS);

        if (currentUsage != null && currentUsage > dailyLimit) {
            log.warn("Token 超限: userId={}, bot={}, usage={}, limit={}",
                    userId, botType, currentUsage, dailyLimit);
            // 回滚
            redisTemplate.opsForValue().decrement(userDailyKey, tokens);
            return false;
        }
        return true;
    }

    /**
     * 获取用户今日 Token 使用量
     */
    public long getDailyUsage(String userId) {
        String today = LocalDate.now().toString();
        String key = String.format(DAILY_KEY_PATTERN, userId, today);
        String val = redisTemplate.opsForValue().get(key);
        return val != null ? Long.parseLong(val) : 0L;
    }

    /**
     * 获取 Bot 今日 Token 使用量
     */
    public long getBotDailyUsage(String botType, String userId) {
        String today = LocalDate.now().toString();
        String key = String.format(BOT_DAILY_KEY_PATTERN, botType, userId, today);
        String val = redisTemplate.opsForValue().get(key);
        return val != null ? Long.parseLong(val) : 0L;
    }

    /**
     * 重置用户配额（管理员操作）
     */
    public void resetUserQuota(String userId) {
        String today = LocalDate.now().toString();
        String key = String.format(DAILY_KEY_PATTERN, userId, today);
        redisTemplate.delete(key);
        log.info("已重置用户 Token 配额: userId={}", userId);
    }
}
