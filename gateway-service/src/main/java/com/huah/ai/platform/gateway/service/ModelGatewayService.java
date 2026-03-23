package com.huah.ai.platform.gateway.service;

import com.huah.ai.platform.common.exception.AiServiceException;
import com.huah.ai.platform.gateway.config.ModelRegistryConfig;
import com.huah.ai.platform.gateway.config.ModelRegistryConfig.ModelDefinition;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class ModelGatewayService {

    private final ModelRegistryConfig registryConfig;
    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;
    private final Map<String, ChatModel> modelCache = new ConcurrentHashMap<>();
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    private final Map<String, ModelStats> statsMap = new ConcurrentHashMap<>();

    public ModelGatewayService(ModelRegistryConfig registryConfig, JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.registryConfig = registryConfig;
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing model registry");
        if (registryConfig.getRegistry() == null) {
            log.warn("No model registry configured");
            return;
        }

        for (ModelDefinition definition : registryConfig.getRegistry()) {
            if (!definition.isEnabled()) {
                continue;
            }
            try {
                ChatModel model = buildChatModel(definition);
                modelCache.put(definition.getId(), model);
                statsMap.put(definition.getId(), new ModelStats(definition.getId()));
                log.info("Registered model: id={}, provider={}, name={}",
                        definition.getId(), definition.getProvider(), definition.getName());
            } catch (Exception e) {
                recordDependencyFailure("model-init", definition.getId());
                log.error("Failed to initialize model: id={}, error={}", definition.getId(), e.getMessage());
            }
        }

        loadStatsFromDb();
        log.info("Model registry initialized, loaded {} models", modelCache.size());
    }

    private void loadStatsFromDb() {
        try {
            jdbcTemplate.query(
                    "SELECT model_id, total_calls, success_calls, total_latency_ms FROM gateway_model_stats",
                    rs -> {
                        String modelId = rs.getString("model_id");
                        int totalCalls = rs.getInt("total_calls");
                        int successCalls = rs.getInt("success_calls");
                        long totalLatencyMs = rs.getLong("total_latency_ms");

                        ModelStats stats = statsMap.computeIfAbsent(modelId, ModelStats::new);
                        stats.restore(totalCalls, successCalls, totalLatencyMs);
                    });
        } catch (Exception e) {
            recordDependencyFailure("database", "load-model-stats");
            log.warn("Failed to load model stats, ignoring on startup: {}", e.getMessage());
        }
    }

    private void persistStats(String modelId, ModelStats stats) {
        try {
            int total = stats.getTotalCalls().get();
            int success = stats.getSuccessCalls().get();
            long totalLatency = stats.getTotalLatencyMs();

            jdbcTemplate.update("""
                INSERT INTO gateway_model_stats (model_id, total_calls, success_calls, total_latency_ms, updated_at)
                VALUES (?, ?, ?, ?, NOW())
                ON CONFLICT (model_id) DO UPDATE SET
                    total_calls = EXCLUDED.total_calls,
                    success_calls = EXCLUDED.success_calls,
                    total_latency_ms = EXCLUDED.total_latency_ms,
                    updated_at = NOW()
            """, modelId, total, success, totalLatency);
        } catch (Exception e) {
            recordDependencyFailure("database", "persist-model-stats");
            log.warn("Failed to persist model stats: modelId={}, error={}", modelId, e.getMessage());
        }
    }

    public ChatClient getChatClient(String scene) {
        String modelId = selectModel(scene);
        ChatModel model = modelCache.get(modelId);
        if (model == null) {
            throw new AiServiceException("No available model for id " + modelId);
        }
        return ChatClient.create(model);
    }

    public ChatClient getChatClientById(String modelId) {
        ChatModel model = modelCache.get(modelId);
        if (model == null) {
            throw new AiServiceException("Model not found: " + modelId);
        }
        return ChatClient.create(model);
    }

    String selectModel(String scene) {
        Map<String, List<String>> sceneRoutes = registryConfig.getSceneRoutes();
        if (scene != null && sceneRoutes != null && sceneRoutes.containsKey(scene)) {
            List<String> candidates = sceneRoutes.get(scene).stream()
                    .filter(modelCache::containsKey)
                    .toList();
            if (!candidates.isEmpty()) {
                return loadBalance(candidates);
            }
        }

        List<String> allModels = registryConfig.getRegistry() != null
                ? registryConfig.getRegistry().stream()
                .map(ModelDefinition::getId)
                .filter(modelCache::containsKey)
                .toList()
                : List.of();
        if (allModels.isEmpty()) {
            allModels = new ArrayList<>(modelCache.keySet());
        }
        if (allModels.isEmpty()) {
            throw new AiServiceException("No available models");
        }
        return loadBalance(allModels);
    }

    private String loadBalance(List<String> candidates) {
        String strategy = registryConfig.getLoadBalanceStrategy();
        return switch (strategy) {
            case "weighted" -> weightedSelect(candidates);
            case "least-latency" -> leastLatencySelect(candidates);
            default -> roundRobinSelect(candidates);
        };
    }

    private String roundRobinSelect(List<String> candidates) {
        int idx = roundRobinCounter.getAndIncrement() % candidates.size();
        return candidates.get(idx);
    }

    private String weightedSelect(List<String> candidates) {
        if (registryConfig.getRegistry() == null) {
            return roundRobinSelect(candidates);
        }

        int totalWeight = registryConfig.getRegistry().stream()
                .filter(definition -> candidates.contains(definition.getId()))
                .mapToInt(ModelDefinition::getWeight)
                .sum();
        if (totalWeight <= 0) {
            return candidates.get(0);
        }

        int rand = new Random().nextInt(totalWeight);
        int cumulative = 0;
        for (ModelDefinition definition : registryConfig.getRegistry()) {
            if (!candidates.contains(definition.getId())) {
                continue;
            }
            cumulative += definition.getWeight();
            if (rand < cumulative) {
                return definition.getId();
            }
        }
        return candidates.get(0);
    }

    private String leastLatencySelect(List<String> candidates) {
        return candidates.stream()
                .min(Comparator.comparingDouble(id ->
                        statsMap.getOrDefault(id, new ModelStats(id)).getAvgLatencyMs()))
                .orElse(candidates.get(0));
    }

    private ChatModel buildChatModel(ModelDefinition definition) {
        return switch (definition.getProvider().toLowerCase()) {
            case "openai", "deepseek", "qwen", "zhipu", "moonshot" -> buildOpenAiCompatibleModel(definition);
            case "anthropic" -> buildAnthropicModel(definition);
            case "ollama" -> buildOllamaModel(definition);
            default -> throw new AiServiceException("Unsupported model provider: " + definition.getProvider());
        };
    }

    private ChatModel buildOpenAiCompatibleModel(ModelDefinition definition) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(definition.getBaseUrl())
                .apiKey(definition.getApiKey())
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(definition.getName())
                .temperature(0.7)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    private ChatModel buildAnthropicModel(ModelDefinition definition) {
        AnthropicApi api = AnthropicApi.builder()
                .baseUrl(definition.getBaseUrl() != null && !definition.getBaseUrl().isBlank()
                        ? definition.getBaseUrl()
                        : AnthropicApi.DEFAULT_BASE_URL)
                .apiKey(definition.getApiKey())
                .build();
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(definition.getName())
                .temperature(0.7)
                .maxTokens(4096)
                .build();
        return AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(options)
                .build();
    }

    private ChatModel buildOllamaModel(ModelDefinition definition) {
        OllamaApi api = OllamaApi.builder()
                .baseUrl(definition.getBaseUrl())
                .build();
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(definition.getName())
                .temperature(0.7)
                .build();
        return OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(options)
                .build();
    }

    public void recordCall(String modelId, long latencyMs, boolean success) {
        String safeModelId = modelId == null || modelId.isBlank() ? "auto" : modelId;
        ModelStats stats = statsMap.computeIfAbsent(safeModelId, ModelStats::new);
        stats.record(latencyMs, success);
        persistStats(safeModelId, stats);

        Timer.builder("gateway.model.call.latency")
                .description("Gateway external model call latency")
                .tag("model", safeModelId)
                .tag("success", String.valueOf(success))
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(latencyMs, TimeUnit.MILLISECONDS);

        Counter.builder("gateway.model.call.total")
                .description("Gateway external model call count")
                .tag("model", safeModelId)
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .increment();
    }

    public Map<String, ModelStats> getAllStats() {
        return Collections.unmodifiableMap(statsMap);
    }

    private void recordDependencyFailure(String dependency, String operation) {
        Counter.builder("gateway.dependency.failures")
                .description("Gateway dependency failure count")
                .tag("dependency", dependency)
                .tag("operation", operation)
                .register(meterRegistry)
                .increment();
    }

    @lombok.Data
    public static class ModelStats {
        private final String modelId;
        private final AtomicInteger totalCalls = new AtomicInteger(0);
        private final AtomicInteger successCalls = new AtomicInteger(0);
        private volatile long totalLatencyMs = 0;
        private volatile double avgLatencyMs = 0;

        public ModelStats(String modelId) {
            this.modelId = modelId;
        }

        public synchronized void record(long latencyMs, boolean success) {
            int n = totalCalls.incrementAndGet();
            if (success) {
                successCalls.incrementAndGet();
            }
            totalLatencyMs += latencyMs;
            avgLatencyMs = (double) totalLatencyMs / n;
        }

        public void restore(int total, int success, long totalLatency) {
            totalCalls.set(total);
            successCalls.set(success);
            totalLatencyMs = totalLatency;
            avgLatencyMs = total > 0 ? (double) totalLatency / total : 0;
        }

        public double getSuccessRate() {
            int total = totalCalls.get();
            return total == 0 ? 1.0 : (double) successCalls.get() / total;
        }
    }
}
