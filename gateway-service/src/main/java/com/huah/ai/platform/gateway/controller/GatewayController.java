package com.huah.ai.platform.gateway.controller;

import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.gateway.config.ModelRegistryConfig;
import com.huah.ai.platform.gateway.model.ChatRequest;
import com.huah.ai.platform.gateway.model.ChatResponse;
import com.huah.ai.platform.gateway.service.ModelGatewayService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class GatewayController {

    private static final String DEFAULT_SCENE = "default";
    private static final String HEADER_SCENE = "X-Scene";
    private static final String HEADER_MODEL_ID = "X-Model-Id";
    private static final String FIELD_STRATEGY = "strategy";
    private static final String PROVIDER_UNKNOWN = "unknown";
    private static final String STRATEGY_ROUND_ROBIN = "round-robin";
    private static final String STRATEGY_WEIGHTED = "weighted";
    private static final String STRATEGY_LEAST_LATENCY = "least-latency";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final long STREAM_TIMEOUT_MS = 60_000L;
    private static final long EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 5L;
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_SERVICE_UNAVAILABLE = 503;
    private static final String MESSAGE_SERVICE_UNAVAILABLE = "AI service is temporarily unavailable because circuit breaker protection was triggered. Please retry later.";
    private static final String MESSAGE_STRATEGY_REQUIRED = "strategy must not be blank";
    private static final String MESSAGE_UNSUPPORTED_STRATEGY = "Unsupported strategy: %s. Supported values: round-robin, weighted, least-latency";
    private static final String MESSAGE_STRATEGY_UPDATED = "Load balance strategy updated to: %s";
    private static final String MESSAGE_SELECTED_MODEL = "Selected as the final model for the current scene";
    private static final String MESSAGE_DEGRADED_MODEL = "Model is currently degraded";
    private static final String MESSAGE_UNHEALTHY_CANDIDATE = "Model did not enter the healthy candidate pool";
    private static final String MESSAGE_NOT_SELECTED = "Model entered the candidate pool but was not selected by the current load-balance strategy";
    private static final String DEFAULT_HEALTH_STATUS = "healthy";
    private static final String EMPTY_CHUNK = "";
    private static final double DEFAULT_SUCCESS_RATE = 100.0d;
    private static final int COST_SCALE = 100000;
    private static final int PERCENT_SCALE = 1000;
    private static final List<String> SUPPORTED_STRATEGIES = List.of(
            STRATEGY_ROUND_ROBIN,
            STRATEGY_WEIGHTED,
            STRATEGY_LEAST_LATENCY
    );

    private final ModelGatewayService gatewayService;
    private final ModelRegistryConfig registryConfig;
    private final ExecutorService executor = Executors.newCachedThreadPool(new GatewayThreadFactory());

    @PreDestroy
    void shutdownExecutor() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    @PostMapping("/completions")
    @CircuitBreaker(name = "aiGateway", fallbackMethod = "chatFallback")
    @RateLimiter(name = "aiGateway")
    public Result<ChatResponse> chat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = HEADER_SCENE, defaultValue = DEFAULT_SCENE) String scene,
            @RequestHeader(value = HEADER_MODEL_ID, required = false) String modelId) {

        long start = System.currentTimeMillis();
        ModelGatewayService.RouteDecision routeDecision = gatewayService.selectModelWithDecision(scene, modelId);
        String usedModelId = routeDecision.getSelectedModelId();

        try {
            ChatClient client = gatewayService.getChatClient(routeDecision);
            List<Message> messages = buildMessages(request);

            org.springframework.ai.chat.model.ChatResponse aiChatResponse = client.prompt()
                    .messages(messages)
                    .call()
                    .chatResponse();
            String content = aiChatResponse.getResult() != null && aiChatResponse.getResult().getOutput() != null
                    ? aiChatResponse.getResult().getOutput().getText()
                    : "";

            long latency = System.currentTimeMillis() - start;
            Map<String, Object> usage = gatewayService.extractUsage(aiChatResponse);
            int promptTokens = ((Number) usage.get("promptTokens")).intValue();
            int completionTokens = ((Number) usage.get("completionTokens")).intValue();
            gatewayService.recordCall(usedModelId, latency, true, promptTokens, completionTokens);

            return Result.ok(buildChatResponse(routeDecision, content, latency, promptTokens, completionTokens));
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            gatewayService.recordCall(usedModelId, latency, false, 0, 0);
            throw e;
        }
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = HEADER_SCENE, defaultValue = DEFAULT_SCENE) String scene,
            @RequestHeader(value = HEADER_MODEL_ID, required = false) String modelId) {

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        ModelGatewayService.RouteDecision routeDecision = gatewayService.selectModelWithDecision(scene, modelId);
        String usedModelId = routeDecision.getSelectedModelId();
        long startTime = System.currentTimeMillis();
        ChatClient client = gatewayService.getChatClient(routeDecision);

        executor.submit(() -> {
            try {
                List<Message> messages = buildMessages(request);
                client.prompt()
                        .messages(messages)
                        .stream()
                        .content()
                        .doOnNext(chunk -> {
                            try {
                                emitter.send(SseEmitter.event().data(buildStreamEvent(chunk, false, usedModelId, null)));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnComplete(() -> {
                            long latency = System.currentTimeMillis() - startTime;
                            gatewayService.recordCall(usedModelId, latency, true, 0, 0);
                            try {
                                emitter.send(SseEmitter.event().data(buildStreamEvent(EMPTY_CHUNK, true, usedModelId, routeDecision)));
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnError(error -> {
                            long latency = System.currentTimeMillis() - startTime;
                            gatewayService.recordCall(usedModelId, latency, false, 0, 0);
                            emitter.completeWithError(error);
                        })
                        .subscribe();
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - startTime;
                gatewayService.recordCall(usedModelId, latency, false, 0, 0);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @GetMapping("/models")
    public Result<Map<String, Object>> listModels() {
        List<Map<String, Object>> models = new ArrayList<>();
        List<ModelRegistryConfig.ModelDefinition> registry = registryConfig.getRegistry();

        if (registry != null) {
            var statsMap = gatewayService.getAllStats();
            var healthMap = gatewayService.getAllHealth();
            for (ModelRegistryConfig.ModelDefinition def : registry) {
                models.add(buildModelView(def, statsMap.get(def.getId()), healthMap.get(def.getId())));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("models", models);
        result.put("count", models.size());
        result.put("sceneRoutes", registryConfig.getSceneRoutes() != null ? registryConfig.getSceneRoutes() : Map.of());
        result.put("loadBalanceStrategy", registryConfig.getLoadBalanceStrategy());
        return Result.ok(result);
    }

    @PostMapping("/models/health/probe")
    public Result<Map<String, Object>> probeAllModels() {
        return Result.ok(gatewayService.probeAllModels());
    }

    @PostMapping("/models/{modelId}/health/probe")
    public Result<Map<String, Object>> probeModel(@PathVariable("modelId") String modelId) {
        return Result.ok(gatewayService.probeModelHealth(modelId));
    }

    @GetMapping("/route-decision")
    public Result<Map<String, Object>> previewRouteDecision(
            @RequestParam(name = "scene", defaultValue = DEFAULT_SCENE) String scene,
            @RequestParam(name = "requestedModelId", required = false) String requestedModelId) {
        ModelGatewayService.RouteDecision decision = gatewayService.selectModelWithDecision(scene, requestedModelId);
        return Result.ok(buildRouteDecisionPreview(decision));
    }

    public Result<ChatResponse> chatFallback(ChatRequest request, String scene, String modelId, Exception e) {
        log.error("AI gateway fallback triggered, modelId={}, error={}", modelId, e.getMessage());
        return Result.fail(HTTP_SERVICE_UNAVAILABLE, MESSAGE_SERVICE_UNAVAILABLE);
    }

    @PutMapping("/config/load-balance")
    public Result<String> updateLoadBalance(@RequestBody Map<String, String> body) {
        String strategy = body.get(FIELD_STRATEGY);
        if (isBlank(strategy)) {
            return Result.fail(HTTP_BAD_REQUEST, MESSAGE_STRATEGY_REQUIRED);
        }
        if (!SUPPORTED_STRATEGIES.contains(strategy)) {
            return Result.fail(HTTP_BAD_REQUEST, MESSAGE_UNSUPPORTED_STRATEGY.formatted(strategy));
        }
        registryConfig.setLoadBalanceStrategy(strategy);
        log.info("Load balance strategy updated to: {}", strategy);
        return Result.ok(MESSAGE_STRATEGY_UPDATED.formatted(strategy));
    }

    private List<Message> buildMessages(ChatRequest request) {
        List<Message> messages = new ArrayList<>();
        if (!isBlank(request.getSystemPrompt())) {
            messages.add(new SystemMessage(request.getSystemPrompt()));
        }
        if (request.getHistory() != null) {
            for (ChatRequest.HistoryMessage history : request.getHistory()) {
                if (history == null || isBlank(history.getContent())) {
                    continue;
                }
                if (ROLE_USER.equals(history.getRole())) {
                    messages.add(new UserMessage(history.getContent()));
                } else if (ROLE_ASSISTANT.equals(history.getRole())) {
                    messages.add(new AssistantMessage(history.getContent()));
                }
            }
        }
        if (!isBlank(request.getMessage())) {
            messages.add(new UserMessage(request.getMessage()));
        }
        return messages;
    }

    private ChatResponse buildChatResponse(ModelGatewayService.RouteDecision routeDecision,
                                           String content,
                                           long latency,
                                           int promptTokens,
                                           int completionTokens) {
        return ChatResponse.builder()
                .content(content)
                .model(routeDecision.getSelectedModelId())
                .latencyMs(latency)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .estimatedCost(calculateEstimatedCost(routeDecision, promptTokens, completionTokens))
                .routeDecision(routeDecision.toMap())
                .build();
    }

    private double calculateEstimatedCost(ModelGatewayService.RouteDecision routeDecision, int promptTokens, int completionTokens) {
        ModelRegistryConfig.ModelDefinition definition = gatewayService.getModelDefinition(routeDecision.getSelectedModelId());
        if (definition == null) {
            return 0d;
        }
        double promptCost = (promptTokens / 1000.0d) * definition.getPromptCostPer1kTokens();
        double completionCost = (completionTokens / 1000.0d) * definition.getCompletionCostPer1kTokens();
        return roundCost(promptCost + completionCost);
    }

    private Map<String, Object> buildRouteDecisionPreview(ModelGatewayService.RouteDecision decision) {
        List<Map<String, Object>> candidateModels = new ArrayList<>();
        Map<String, ModelGatewayService.ModelStats> statsMap = gatewayService.getAllStats();
        Map<String, ModelGatewayService.ModelHealth> healthMap = gatewayService.getAllHealth();
        for (String candidateId : defaultList(decision.getCandidateModelIds())) {
            ModelRegistryConfig.ModelDefinition definition = gatewayService.getModelDefinition(candidateId);
            var stats = statsMap.get(candidateId);
            var health = healthMap.get(candidateId);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", candidateId);
            item.put("name", definition != null ? definition.getName() : candidateId);
            item.put("provider", definition != null ? definition.getProvider() : PROVIDER_UNKNOWN);
            item.put("enabled", definition == null || definition.isEnabled());
            item.put("healthy", defaultList(decision.getHealthyCandidateModelIds()).contains(candidateId));
            item.put("selected", candidateId.equals(decision.getSelectedModelId()));
            item.put("degraded", defaultList(decision.getDegradedModelIds()).contains(candidateId));
            item.put("weight", definition != null ? definition.getWeight() : 0);
            item.put("avgLatencyMs", stats != null ? Math.round(stats.getAvgLatencyMs()) : 0);
            item.put("successRate", stats != null ? roundPercentage(stats.getSuccessRate()) : DEFAULT_SUCCESS_RATE);
            item.put("promptCostPer1kTokens", definition != null ? definition.getPromptCostPer1kTokens() : 0d);
            item.put("completionCostPer1kTokens", definition != null ? definition.getCompletionCostPer1kTokens() : 0d);
            item.put("reason", buildCandidateReason(candidateId, decision, health != null ? health.getLastReason() : ""));
            candidateModels.add(item);
        }

        ModelRegistryConfig.ModelDefinition selectedDefinition = gatewayService.getModelDefinition(decision.getSelectedModelId());

        return Map.of(
                "scene", decision.getScene() == null ? "" : decision.getScene(),
                "requestedModelId", decision.getRequestedModelId() == null ? "" : decision.getRequestedModelId(),
                "selectedModelId", decision.getSelectedModelId() == null ? "" : decision.getSelectedModelId(),
                "strategy", decision.getStrategy() == null ? "" : decision.getStrategy(),
                "reason", decision.getReason() == null ? "" : decision.getReason(),
                "fallbackTriggered", decision.isFallbackTriggered(),
                "estimatedCostNote", buildEstimatedCostNote(selectedDefinition),
                "candidateModels", candidateModels
        );
    }

    private String buildCandidateReason(String candidateId, ModelGatewayService.RouteDecision decision, String healthReason) {
        if (candidateId.equals(decision.getSelectedModelId())) {
            return MESSAGE_SELECTED_MODEL;
        }
        if (defaultList(decision.getDegradedModelIds()).contains(candidateId)) {
            return isBlank(healthReason) ? MESSAGE_DEGRADED_MODEL : healthReason;
        }
        if (!defaultList(decision.getHealthyCandidateModelIds()).contains(candidateId)) {
            return MESSAGE_UNHEALTHY_CANDIDATE;
        }
        return MESSAGE_NOT_SELECTED;
    }

    private Map<String, Object> buildStreamEvent(String chunk,
                                                 boolean done,
                                                 String modelId,
                                                 ModelGatewayService.RouteDecision routeDecision) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("chunk", chunk);
        event.put("done", done);
        event.put("model", modelId);
        if (routeDecision != null) {
            event.put("routeDecision", routeDecision.toMap());
        }
        return event;
    }

    private Map<String, Object> buildModelView(ModelRegistryConfig.ModelDefinition definition,
                                               ModelGatewayService.ModelStats stats,
                                               ModelGatewayService.ModelHealth health) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("id", definition.getId());
        model.put("name", definition.getName());
        model.put("provider", definition.getProvider());
        model.put("enabled", definition.isEnabled());
        model.put("weight", definition.getWeight());
        model.put("capabilities", definition.getCapabilities());
        model.put("rpmLimit", definition.getRpmLimit());
        model.put("promptCostPer1kTokens", definition.getPromptCostPer1kTokens());
        model.put("completionCostPer1kTokens", definition.getCompletionCostPer1kTokens());
        model.put("healthStatus", health != null ? health.getStatus() : DEFAULT_HEALTH_STATUS);
        model.put("degradedUntil", health != null && health.getDegradedUntil() > 0 ? health.getDegradedUntil() : null);
        model.put("healthReason", health != null ? health.getLastReason() : "");
        model.put("consecutiveFailures", health != null ? health.getConsecutiveFailures().get() : 0);
        model.put("lastCheckedAt", health != null && health.getLastCheckedAt() > 0 ? health.getLastCheckedAt() : null);
        model.put("lastProbeLatencyMs", health != null ? health.getLastProbeLatencyMs() : null);
        model.put("totalCalls", stats != null ? stats.getTotalCalls().get() : 0);
        model.put("successCalls", stats != null ? stats.getSuccessCalls().get() : 0);
        model.put("avgLatencyMs", stats != null ? Math.round(stats.getAvgLatencyMs()) : 0);
        model.put("successRate", stats != null ? roundPercentage(stats.getSuccessRate()) : DEFAULT_SUCCESS_RATE);
        model.put("totalPromptTokens", stats != null ? stats.getTotalPromptTokens() : 0);
        model.put("totalCompletionTokens", stats != null ? stats.getTotalCompletionTokens() : 0);
        model.put("totalEstimatedCost", stats != null ? roundCost(stats.getTotalEstimatedCost()) : 0d);
        return model;
    }

    private double roundPercentage(double value) {
        return Math.round(value * PERCENT_SCALE) / 10.0d;
    }

    private double roundCost(double value) {
        return Math.round(value * COST_SCALE) / (double) COST_SCALE;
    }

    private String buildEstimatedCostNote(ModelRegistryConfig.ModelDefinition selectedDefinition) {
        if (selectedDefinition == null) {
            return "";
        }
        return "Estimated per 1K tokens: prompt "
                + selectedDefinition.getPromptCostPer1kTokens()
                + ", completion "
                + selectedDefinition.getCompletionCostPer1kTokens();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private List<String> defaultList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static final class GatewayThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "gateway-sse-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
