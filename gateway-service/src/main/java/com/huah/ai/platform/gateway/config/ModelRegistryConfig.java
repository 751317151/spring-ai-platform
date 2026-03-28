package com.huah.ai.platform.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Multi-model registry configuration.
 * Supports OpenAI-compatible providers, Anthropic, Ollama, and related routing settings.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.models")
public class ModelRegistryConfig {

    /**
     * Default model id.
     */
    private String defaultModel = "gpt-4o-mini";

    /**
     * Load-balance strategy: round-robin | weighted | least-latency.
     */
    private String loadBalanceStrategy = "round-robin";

    /**
     * Registered model definitions.
     */
    private List<ModelDefinition> registry;

    /**
     * Scene route mapping: scene name -> preferred model ids.
     */
    private Map<String, List<String>> sceneRoutes;

    /**
     * Active health probe configuration.
     */
    private HealthProbe healthProbe = new HealthProbe();

    @Data
    public static class ModelDefinition {
        /** Stable model identifier. */
        private String id;
        /** Display name. */
        private String name;
        /** Provider id: openai | anthropic | ollama | qwen | deepseek. */
        private String provider;
        /** API key. */
        private String apiKey;
        /** Base URL for OpenAI-compatible or self-hosted providers. */
        private String baseUrl;
        /** Whether the model is enabled. */
        private boolean enabled = true;
        /** Weight used by weighted load balancing. */
        private int weight = 1;
        /** Maximum concurrent requests. */
        private int maxConcurrency = 20;
        /** Request timeout in milliseconds. */
        private long timeoutMs = 30000;
        /** Whether streaming responses are enabled. */
        private boolean streaming = true;
        /** Capability tags: chat | embedding | image | code. */
        private List<String> capabilities;
        /** Requests-per-minute limit. */
        private int rpmLimit = 60;
        /** Prompt token cost per 1K tokens. */
        private double promptCostPer1kTokens = 0;
        /** Completion token cost per 1K tokens. */
        private double completionCostPer1kTokens = 0;
    }

    @Data
    public static class HealthProbe {
        /** Whether active probing is enabled. */
        private boolean enabled = true;
        /** Probe interval in milliseconds. */
        private long intervalMs = 300000;
        /** Probe prompt content. */
        private String prompt = "ping";
        /** Whether to probe immediately on startup. */
        private boolean runOnStartup = true;
    }
}
