package com.huah.ai.platform.agent.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfiguration {

    public static final String CACHE_SESSION_CONFIG = "sessionConfig";
    public static final String CACHE_AGENT_METADATA = "agentMetadata";
    public static final String CACHE_DYNAMIC_AGENT_CAPABILITIES = "dynamicAgentCapabilities";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .recordStats());
        cacheManager.setCacheNames(java.util.List.of(
                CACHE_SESSION_CONFIG,
                CACHE_AGENT_METADATA,
                CACHE_DYNAMIC_AGENT_CAPABILITIES));
        return cacheManager;
    }
}
