package com.huah.ai.platform.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 多模型注册表配置
 * 支持：OpenAI、DeepSeek、通义千问、文心一言、Ollama 本地模型等
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.models")
public class ModelRegistryConfig {

    /**
     * 默认模型
     */
    private String defaultModel = "gpt-4o-mini";

    /**
     * 负载均衡策略: round-robin | weighted | least-latency
     */
    private String loadBalanceStrategy = "round-robin";

    /**
     * 注册的模型列表
     */
    private List<ModelDefinition> registry;

    /**
     * 场景路由规则：场景名 -> 优先模型列表
     */
    private Map<String, List<String>> sceneRoutes;

    /**
     * 主动健康探测配置
     */
    private HealthProbe healthProbe = new HealthProbe();

    @Data
    public static class ModelDefinition {
        /** 模型唯一标识 */
        private String id;
        /** 模型名称 */
        private String name;
        /** 提供商: openai | anthropic | ollama | qwen | deepseek */
        private String provider;
        /** API Key */
        private String apiKey;
        /** Base URL（用于兼容 OpenAI 协议的国产模型） */
        private String baseUrl;
        /** 是否启用 */
        private boolean enabled = true;
        /** 权重（用于加权负载均衡） */
        private int weight = 1;
        /** 最大并发数 */
        private int maxConcurrency = 20;
        /** 超时（毫秒） */
        private long timeoutMs = 30000;
        /** 是否支持流式 */
        private boolean streaming = true;
        /** 模型能力标签: chat | embedding | image | code */
        private List<String> capabilities;
        /** 每分钟 Token 限制 */
        private int rpmLimit = 60;
        /** 输入 token 单价，单位：每 1k token */
        private double promptCostPer1kTokens = 0;
        /** 输出 token 单价，单位：每 1k token */
        private double completionCostPer1kTokens = 0;
    }

    @Data
    public static class HealthProbe {
        /** 是否启用主动健康探测 */
        private boolean enabled = true;
        /** 探测间隔毫秒 */
        private long intervalMs = 300000;
        /** 单次探测提示词 */
        private String prompt = "ping";
        /** 启动后是否立即探测 */
        private boolean runOnStartup = true;
    }
}
