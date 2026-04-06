package com.huah.ai.platform.gateway.service;

import com.huah.ai.platform.common.exception.AiServiceException;
import com.huah.ai.platform.gateway.config.ModelRegistryConfig;
import com.huah.ai.platform.gateway.config.ModelRegistryConfig.ModelDefinition;
import com.huah.ai.platform.gateway.model.GatewayModelProbeResponse;
import com.huah.ai.platform.gateway.model.GatewayProbeSummaryResponse;
import com.huah.ai.platform.gateway.model.GatewayUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class ModelGatewayService {
    private static final int DEGRADE_FAILURE_THRESHOLD = 3;
    private static final long DEGRADE_COOLDOWN_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final String STRATEGY_MANUAL = "manual";
    private static final String STRATEGY_ROUND_ROBIN = "round-robin";
    private static final String STRATEGY_WEIGHTED = "weighted";
    private static final String STRATEGY_LEAST_LATENCY = "least-latency";
    private static final String STATUS_HEALTHY = "healthy";
    private static final String STATUS_DEGRADED = "degraded";
    private static final String SAFE_MODEL_ID_AUTO = "auto";
    private static final String DEFAULT_PROBE_PROMPT = "ping";
    private static final String REASON_MANUAL_OVERRIDE = "Matched explicit model selection and skipped automatic routing";
    private static final String REASON_SCENE_HEALTHY_ROUTE = "Matched scene routing and selected from healthy candidates";
    private static final String REASON_SCENE_DEGRADED_FALLBACK = "Matched scene routing but no healthy candidates remained, so routing fallback to degraded candidates";
    private static final String REASON_GLOBAL_HEALTHY_ROUTE = "No scene-specific route matched, selected from globally healthy models";
    private static final String REASON_GLOBAL_FALLBACK = "No scene-specific healthy route matched, selected from the full model pool as fallback";
    private static final String REASON_PROBE_SUCCESS = "Active health probe succeeded";
    private static final String REASON_PROBE_FAILED_PREFIX = "Active health probe failed: ";
    private static final String REASON_CONSECUTIVE_FAILURES = "Consecutive failures: %d";
    private static final String REASON_DEGRADED = "Too many consecutive failures, model temporarily degraded";

    private final ModelRegistryConfig registryConfig;
    private final MeterRegistry meterRegistry;
    private final GatewayModelClientFactory clientFactory;
    private final GatewayModelStatsStore statsStore;
    private final Map<String, ChatModel> modelCache = new ConcurrentHashMap<>();
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    private final Map<String, ModelStats> statsMap = new ConcurrentHashMap<>();
    private final Map<String, ModelHealth> healthMap = new ConcurrentHashMap<>();

    public ModelGatewayService(ModelRegistryConfig registryConfig, JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.registryConfig = registryConfig;
        this.meterRegistry = meterRegistry;
        this.clientFactory = new GatewayModelClientFactory();
        this.statsStore = new GatewayModelStatsStore(jdbcTemplate);
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
                ChatModel model = clientFactory.buildChatModel(definition);
                modelCache.put(definition.getId(), model);
                statsMap.put(definition.getId(), new ModelStats(definition.getId()));
                healthMap.put(definition.getId(), new ModelHealth(definition.getId()));
                log.info("Registered model: id={}, provider={}, name={}",
                        definition.getId(), definition.getProvider(), definition.getName());
            } catch (IllegalArgumentException | AiServiceException e) {
                recordDependencyFailure("model-init", definition.getId());
                log.error("Failed to initialize model: id={}, error={}", definition.getId(), e.getMessage(), e);
            }
        }

        loadStatsFromDb();
        log.info("Model registry initialized, loaded {} models", modelCache.size());
    }

    private void loadStatsFromDb() {
        try {
            statsStore.loadStats().forEach((modelId, persistedStats) -> {
                ModelStats stats = statsMap.computeIfAbsent(modelId, ModelStats::new);
                stats.restore(
                        persistedStats.getTotalCalls().get(),
                        persistedStats.getSuccessCalls().get(),
                        persistedStats.getTotalLatencyMs(),
                        persistedStats.getTotalPromptTokens(),
                        persistedStats.getTotalCompletionTokens(),
                        persistedStats.getTotalEstimatedCost()
                );
            });
        } catch (RuntimeException e) {
            if (!statsStore.isRecoverableDataAccessException(e)) {
                throw e;
            }
            recordDependencyFailure("database", "load-model-stats");
            log.warn("Failed to load model stats on startup. Verify gateway_model_stats schema is up to date.", e);
        }
    }

    private void persistStats(String modelId, ModelStats stats) {
        try {
            statsStore.persistStats(modelId, stats);
        } catch (RuntimeException e) {
            if (!statsStore.isRecoverableDataAccessException(e)) {
                throw e;
            }
            recordDependencyFailure("database", "persist-model-stats");
            log.warn("Failed to persist model stats: modelId={}", modelId, e);
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

    public ChatClient getChatClient(RouteDecision decision) {
        ChatModel model = modelCache.get(decision.getSelectedModelId());
        if (model == null) {
            throw new AiServiceException("No available model for id " + decision.getSelectedModelId());
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
        return selectModelWithDecision(scene, null).getSelectedModelId();
    }

    public RouteDecision selectModelWithDecision(String scene, String requestedModelId) {
        if (requestedModelId != null && !requestedModelId.isBlank()) {
            if (!modelCache.containsKey(requestedModelId)) {
                throw new AiServiceException("Model not found: " + requestedModelId);
            }
            return RouteDecision.builder()
                    .scene(scene)
                    .requestedModelId(requestedModelId)
                    .selectedModelId(requestedModelId)
                    .strategy(STRATEGY_MANUAL)
                    .reason(REASON_MANUAL_OVERRIDE)
                    .candidateModelIds(List.of(requestedModelId))
                    .healthyCandidateModelIds(isHealthyForRouting(requestedModelId) ? List.of(requestedModelId) : List.of())
                    .degradedModelIds(isHealthyForRouting(requestedModelId) ? List.of() : List.of(requestedModelId))
                    .fallbackTriggered(!isHealthyForRouting(requestedModelId))
                    .build();
        }

        Map<String, List<String>> sceneRoutes = registryConfig.getSceneRoutes();
        if (scene != null && sceneRoutes != null && sceneRoutes.containsKey(scene)) {
            List<String> candidates = sceneRoutes.get(scene).stream()
                    .filter(modelCache::containsKey)
                    .toList();
            List<String> healthyCandidates = preferHealthyCandidates(candidates);
            if (!healthyCandidates.isEmpty()) {
                String selectedModelId = loadBalance(healthyCandidates);
                return RouteDecision.builder()
                        .scene(scene)
                        .selectedModelId(selectedModelId)
                        .strategy(normalizeStrategy())
                        .reason(REASON_SCENE_HEALTHY_ROUTE)
                        .candidateModelIds(candidates)
                        .healthyCandidateModelIds(healthyCandidates)
                        .degradedModelIds(findDegradedCandidates(candidates))
                        .fallbackTriggered(false)
                        .build();
            }
            if (!candidates.isEmpty()) {
                String selectedModelId = loadBalance(candidates);
                return RouteDecision.builder()
                        .scene(scene)
                        .selectedModelId(selectedModelId)
                        .strategy(normalizeStrategy())
                        .reason(REASON_SCENE_DEGRADED_FALLBACK)
                        .candidateModelIds(candidates)
                        .healthyCandidateModelIds(List.of())
                        .degradedModelIds(findDegradedCandidates(candidates))
                        .fallbackTriggered(true)
                        .build();
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

        List<String> healthyModels = preferHealthyCandidates(allModels);
        List<String> routedCandidates = healthyModels.isEmpty() ? allModels : healthyModels;
        boolean fallbackTriggered = healthyModels.isEmpty();
        String selectedModelId = loadBalance(routedCandidates);
        return RouteDecision.builder()
                .scene(scene)
                .selectedModelId(selectedModelId)
                .strategy(normalizeStrategy())
                .reason(fallbackTriggered ? REASON_GLOBAL_FALLBACK : REASON_GLOBAL_HEALTHY_ROUTE)
                .candidateModelIds(allModels)
                .healthyCandidateModelIds(healthyModels)
                .degradedModelIds(findDegradedCandidates(allModels))
                .fallbackTriggered(fallbackTriggered)
                .build();
    }

    private List<String> preferHealthyCandidates(List<String> candidates) {
        return candidates.stream()
                .filter(this::isHealthyForRouting)
                .toList();
    }

    private boolean isHealthyForRouting(String modelId) {
        return !healthMap.computeIfAbsent(modelId, ModelHealth::new).isTemporarilyDegraded();
    }

    private String loadBalance(List<String> candidates) {
        String strategy = registryConfig.getLoadBalanceStrategy();
        return switch (strategy) {
            case STRATEGY_WEIGHTED -> weightedSelect(candidates);
            case STRATEGY_LEAST_LATENCY -> leastLatencySelect(candidates);
            default -> roundRobinSelect(candidates);
        };
    }

    private String normalizeStrategy() {
        String strategy = registryConfig.getLoadBalanceStrategy();
        return (strategy == null || strategy.isBlank()) ? STRATEGY_ROUND_ROBIN : strategy;
    }

    private List<String> findDegradedCandidates(List<String> candidates) {
        return candidates.stream()
                .filter(candidate -> !isHealthyForRouting(candidate))
                .toList();
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

        int rand = ThreadLocalRandom.current().nextInt(totalWeight);
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
                .min(Comparator.comparingDouble(id -> statsMap.getOrDefault(id, new ModelStats(id)).getAvgLatencyMs()))
                .orElse(candidates.get(0));
    }

    public void recordCall(String modelId, long latencyMs, boolean success) {
        recordCall(modelId, latencyMs, success, null, null);
    }

    public void recordCall(String modelId, long latencyMs, boolean success, Integer promptTokens, Integer completionTokens) {
        String safeModelId = modelId == null || modelId.isBlank() ? SAFE_MODEL_ID_AUTO : modelId;
        ModelStats stats = statsMap.computeIfAbsent(safeModelId, ModelStats::new);
        ModelHealth health = healthMap.computeIfAbsent(safeModelId, ModelHealth::new);
        stats.record(latencyMs, success, promptTokens, completionTokens, estimateCost(safeModelId, promptTokens, completionTokens));
        health.record(success);
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

    public Map<String, ModelHealth> getAllHealth() {
        return Collections.unmodifiableMap(healthMap);
    }

    public ModelDefinition getModelDefinition(String modelId) {
        if (registryConfig.getRegistry() == null || modelId == null) {
            return null;
        }
        return registryConfig.getRegistry().stream()
                .filter(item -> modelId.equals(item.getId()))
                .findFirst()
                .orElse(null);
    }

    public GatewayUsage extractUsage(ChatResponse response) {
        int promptTokens = 0;
        int completionTokens = 0;
        if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            promptTokens = response.getMetadata().getUsage().getPromptTokens();
            completionTokens = response.getMetadata().getUsage().getCompletionTokens();
        }
        return GatewayUsage.builder()
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .build();
    }

    public GatewayProbeSummaryResponse probeAllModels() {
        List<GatewayModelProbeResponse> probes = modelCache.keySet().stream()
                .sorted()
                .map(this::probeModelHealth)
                .toList();
        return GatewayProbeSummaryResponse.builder()
                .probes(probes)
                .count(probes.size())
                .build();
    }

    public GatewayModelProbeResponse probeModelHealth(String modelId) {
        ChatModel model = modelCache.get(modelId);
        if (model == null) {
            throw new AiServiceException("Model not found: " + modelId);
        }

        long start = System.currentTimeMillis();
        String prompt = registryConfig.getHealthProbe() != null && registryConfig.getHealthProbe().getPrompt() != null
                ? registryConfig.getHealthProbe().getPrompt()
                : DEFAULT_PROBE_PROMPT;
        ModelHealth health = healthMap.computeIfAbsent(modelId, ModelHealth::new);
        try {
            model.call(new Prompt(prompt));
            long latency = System.currentTimeMillis() - start;
            health.recordProbe(true, latency, REASON_PROBE_SUCCESS);
            return GatewayModelProbeResponse.builder()
                    .modelId(modelId)
                    .status(health.getStatus())
                    .probeLatencyMs(latency)
                    .reason(health.getLastReason())
                    .build();
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            health.recordProbe(false, latency, REASON_PROBE_FAILED_PREFIX + e.getMessage());
            recordDependencyFailure("model-health-probe", modelId);
            log.warn("Model health probe failed: modelId={}, error={}", modelId, e.getMessage());
            return GatewayModelProbeResponse.builder()
                    .modelId(modelId)
                    .status(health.getStatus())
                    .probeLatencyMs(latency)
                    .reason(health.getLastReason())
                    .build();
        }
    }

    private void recordDependencyFailure(String dependency, String operation) {
        Counter.builder("gateway.dependency.failures")
                .description("Gateway dependency failure count")
                .tag("dependency", dependency)
                .tag("operation", operation)
                .register(meterRegistry)
                .increment();
    }

    private double estimateCost(String modelId, Integer promptTokens, Integer completionTokens) {
        ModelDefinition definition = getModelDefinition(modelId);
        if (definition == null) {
            return 0d;
        }
        double promptCost = ((promptTokens == null ? 0 : promptTokens) / 1000.0d) * definition.getPromptCostPer1kTokens();
        double completionCost = ((completionTokens == null ? 0 : completionTokens) / 1000.0d) * definition.getCompletionCostPer1kTokens();
        return promptCost + completionCost;
    }

    @Data
    public static class ModelStats {
        private final String modelId;
        private final AtomicInteger totalCalls = new AtomicInteger(0);
        private final AtomicInteger successCalls = new AtomicInteger(0);
        private volatile long totalLatencyMs = 0;
        private volatile long totalPromptTokens = 0;
        private volatile long totalCompletionTokens = 0;
        private volatile double totalEstimatedCost = 0;
        private volatile double avgLatencyMs = 0;

        public ModelStats(String modelId) {
            this.modelId = modelId;
        }

        public synchronized void record(long latencyMs, boolean success, Integer promptTokens, Integer completionTokens, double estimatedCost) {
            int total = totalCalls.incrementAndGet();
            if (success) {
                successCalls.incrementAndGet();
            }
            totalLatencyMs += latencyMs;
            totalPromptTokens += promptTokens == null ? 0 : promptTokens;
            totalCompletionTokens += completionTokens == null ? 0 : completionTokens;
            totalEstimatedCost += estimatedCost;
            avgLatencyMs = (double) totalLatencyMs / total;
        }

        public synchronized void restore(int total, int success, long totalLatency, long promptTokens, long completionTokens, double estimatedCost) {
            totalCalls.set(total);
            successCalls.set(success);
            totalLatencyMs = totalLatency;
            totalPromptTokens = promptTokens;
            totalCompletionTokens = completionTokens;
            totalEstimatedCost = estimatedCost;
            avgLatencyMs = total > 0 ? (double) totalLatency / total : 0;
        }

        public double getSuccessRate() {
            int total = totalCalls.get();
            return total == 0 ? 1.0 : (double) successCalls.get() / total;
        }
    }

    @Data
    public static class ModelHealth {
        private final String modelId;
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private volatile long degradedUntil = 0;
        private volatile String lastReason = "";
        private volatile long lastCheckedAt = 0;
        private volatile long lastProbeLatencyMs = -1;

        public ModelHealth(String modelId) {
            this.modelId = modelId;
        }

        public synchronized void record(boolean success) {
            lastCheckedAt = System.currentTimeMillis();
            if (success) {
                consecutiveFailures.set(0);
                degradedUntil = 0;
                lastReason = "";
                return;
            }

            int failures = consecutiveFailures.incrementAndGet();
            lastReason = REASON_CONSECUTIVE_FAILURES.formatted(failures);
            if (failures >= DEGRADE_FAILURE_THRESHOLD) {
                degradedUntil = System.currentTimeMillis() + DEGRADE_COOLDOWN_MILLIS;
                lastReason = REASON_DEGRADED;
            }
        }

        public synchronized void recordProbe(boolean success, long latencyMs, String reason) {
            lastCheckedAt = System.currentTimeMillis();
            lastProbeLatencyMs = latencyMs;
            if (success) {
                consecutiveFailures.set(0);
                degradedUntil = 0;
                lastReason = reason;
                return;
            }

            int failures = consecutiveFailures.incrementAndGet();
            lastReason = reason;
            if (failures >= DEGRADE_FAILURE_THRESHOLD) {
                degradedUntil = System.currentTimeMillis() + DEGRADE_COOLDOWN_MILLIS;
                if (lastReason == null || lastReason.isBlank()) {
                    lastReason = REASON_DEGRADED;
                }
            }
        }

        public boolean isTemporarilyDegraded() {
            return degradedUntil > System.currentTimeMillis();
        }

        public String getStatus() {
            return isTemporarilyDegraded() ? STATUS_DEGRADED : STATUS_HEALTHY;
        }
    }

    @Builder
    @Data
    public static class RouteDecision {
        private String scene;
        private String requestedModelId;
        private String selectedModelId;
        private String strategy;
        private String reason;
        private List<String> candidateModelIds;
        private List<String> healthyCandidateModelIds;
        private List<String> degradedModelIds;
        private boolean fallbackTriggered;
    }
}
