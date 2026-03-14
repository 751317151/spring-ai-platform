package com.huah.ai.platform.gateway.service;

import com.huah.ai.platform.common.exception.AiServiceException;
import com.huah.ai.platform.gateway.config.ModelRegistryConfig;
import com.huah.ai.platform.gateway.config.ModelRegistryConfig.ModelDefinition;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模型服务网关 - 多模型管理、路由、负载均衡
 */
@Slf4j
@Service
public class ModelGatewayService {

    private final ModelRegistryConfig registryConfig;
    private final JdbcTemplate jdbcTemplate;

    /** 模型实例缓存 */
    private final Map<String, ChatModel> modelCache = new ConcurrentHashMap<>();
    /** 轮询计数器 */
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    /** 模型调用统计 */
    private final Map<String, ModelStats> statsMap = new ConcurrentHashMap<>();

    public ModelGatewayService(ModelRegistryConfig registryConfig, JdbcTemplate jdbcTemplate) {
        this.registryConfig = registryConfig;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        // 确保表存在
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS gateway_model_stats (
                model_id        VARCHAR(64) PRIMARY KEY,
                total_calls     INT DEFAULT 0,
                success_calls   INT DEFAULT 0,
                total_latency_ms BIGINT DEFAULT 0,
                updated_at      TIMESTAMP DEFAULT NOW()
            )
        """);

        log.info("初始化模型注册表...");
        if (registryConfig.getRegistry() == null) {
            log.warn("未配置模型注册表，将使用默认注入模型");
            return;
        }
        for (ModelDefinition def : registryConfig.getRegistry()) {
            if (!def.isEnabled()) continue;
            try {
                ChatModel model = buildChatModel(def);
                modelCache.put(def.getId(), model);
                statsMap.put(def.getId(), new ModelStats(def.getId()));
                log.info("已注册模型: id={}, provider={}, name={}", def.getId(), def.getProvider(), def.getName());
            } catch (Exception e) {
                log.error("模型初始化失败: id={}, error={}", def.getId(), e.getMessage());
            }
        }

        // 从数据库加载历史统计
        loadStatsFromDb();
        log.info("模型注册表初始化完成，共注册 {} 个模型", modelCache.size());
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

                    ModelStats stats = statsMap.get(modelId);
                    if (stats == null) {
                        stats = new ModelStats(modelId);
                        statsMap.put(modelId, stats);
                    }
                    stats.restore(totalCalls, successCalls, totalLatencyMs);
                    log.info("已恢复模型统计: id={}, totalCalls={}, successCalls={}, avgLatency={}ms",
                            modelId, totalCalls, successCalls,
                            totalCalls > 0 ? totalLatencyMs / totalCalls : 0);
                }
            );
        } catch (Exception e) {
            log.warn("加载模型统计失败（首次启动可忽略）: {}", e.getMessage());
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
            log.warn("持久化模型统计失败: modelId={}, error={}", modelId, e.getMessage());
        }
    }

    /**
     * 根据场景获取最优 ChatClient
     */
    public ChatClient getChatClient(String scene) {
        String modelId = selectModel(scene);
        ChatModel model = modelCache.get(modelId);
        if (model == null) {
            throw new AiServiceException("未找到可用模型: " + modelId);
        }
        return ChatClient.create(model);
    }

    /**
     * 直接按模型ID获取
     */
    public ChatClient getChatClientById(String modelId) {
        ChatModel model = modelCache.get(modelId);
        if (model == null) {
            throw new AiServiceException("模型不存在: " + modelId);
        }
        return ChatClient.create(model);
    }

    /**
     * 模型路由选择
     */
    private String selectModel(String scene) {
        // 1. 场景路由优先
        Map<String, List<String>> sceneRoutes = registryConfig.getSceneRoutes();
        if (scene != null && sceneRoutes != null && sceneRoutes.containsKey(scene)) {
            List<String> candidates = sceneRoutes.get(scene).stream()
                    .filter(modelCache::containsKey)
                    .toList();
            if (!candidates.isEmpty()) {
                return loadBalance(candidates);
            }
        }
        // 2. 按负载均衡策略选择所有可用模型
        List<String> allModels = new ArrayList<>(modelCache.keySet());
        if (allModels.isEmpty()) {
            throw new AiServiceException("无可用模型");
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
        if (registryConfig.getRegistry() == null) return roundRobinSelect(candidates);
        int totalWeight = registryConfig.getRegistry().stream()
                .filter(d -> candidates.contains(d.getId()))
                .mapToInt(ModelDefinition::getWeight).sum();
        int rand = new Random().nextInt(totalWeight);
        int cumulative = 0;
        for (ModelDefinition def : registryConfig.getRegistry()) {
            if (!candidates.contains(def.getId())) continue;
            cumulative += def.getWeight();
            if (rand < cumulative) return def.getId();
        }
        return candidates.get(0);
    }

    private String leastLatencySelect(List<String> candidates) {
        return candidates.stream()
                .min(Comparator.comparingDouble(id ->
                        statsMap.getOrDefault(id, new ModelStats(id)).getAvgLatencyMs()))
                .orElse(candidates.get(0));
    }

    /**
     * 根据配置构建对应的 ChatModel
     */
    private ChatModel buildChatModel(ModelDefinition def) {
        return switch (def.getProvider().toLowerCase()) {
            case "openai", "deepseek", "qwen", "zhipu", "moonshot" -> buildOpenAiCompatibleModel(def);
            case "anthropic" -> buildAnthropicModel(def);
            case "ollama" -> buildOllamaModel(def);
            default -> throw new AiServiceException("不支持的模型提供商: " + def.getProvider());
        };
    }

    /** OpenAI 协议兼容模型（包括 DeepSeek、通义千问等）*/
    private ChatModel buildOpenAiCompatibleModel(ModelDefinition def) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(def.getBaseUrl())
                .apiKey(def.getApiKey())
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(def.getName())
                .temperature(0.7)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    /** Anthropic Claude */
    private ChatModel buildAnthropicModel(ModelDefinition def) {
        return null;
    }

    /** Ollama 本地模型 */
    private ChatModel buildOllamaModel(ModelDefinition def) {
        OllamaApi api = OllamaApi.builder()
                .baseUrl(def.getBaseUrl())
                .build();
        OllamaOptions options = OllamaOptions.builder()
                .model(def.getName())
                .temperature(0.7)
                .build();
        return OllamaChatModel.builder().ollamaApi(api).defaultOptions(options).build();
    }

    /** 记录调用统计并持久化 */
    public void recordCall(String modelId, long latencyMs, boolean success) {
        ModelStats stats = statsMap.computeIfAbsent(modelId, ModelStats::new);
        stats.record(latencyMs, success);
        persistStats(modelId, stats);
    }

    /** 获取所有模型统计 */
    public Map<String, ModelStats> getAllStats() {
        return Collections.unmodifiableMap(statsMap);
    }

    // 统计类
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
            if (success) successCalls.incrementAndGet();
            totalLatencyMs += latencyMs;
            avgLatencyMs = (double) totalLatencyMs / n;
        }

        /** 从数据库恢复统计数据 */
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
