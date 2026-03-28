package com.huah.ai.platform.gateway.controller;

import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.gateway.config.ModelRegistryConfig;
import com.huah.ai.platform.gateway.model.ChatRequest;
import com.huah.ai.platform.gateway.model.ChatResponse;
import com.huah.ai.platform.gateway.model.GatewayCandidateModelView;
import com.huah.ai.platform.gateway.model.GatewayModelProbeResponse;
import com.huah.ai.platform.gateway.model.GatewayModelsResponse;
import com.huah.ai.platform.gateway.model.GatewayModelView;
import com.huah.ai.platform.gateway.model.GatewayProbeSummaryResponse;
import com.huah.ai.platform.gateway.model.GatewayRouteDecisionResponse;
import com.huah.ai.platform.gateway.model.GatewayStreamEvent;
import com.huah.ai.platform.gateway.model.GatewayUsage;
import com.huah.ai.platform.gateway.model.RouteDecisionPayload;
import com.huah.ai.platform.gateway.service.ModelGatewayService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

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
    @Qualifier("gatewaySseExecutor")
    private final ExecutorService executor;

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
            GatewayUsage usage = gatewayService.extractUsage(aiChatResponse);
            int promptTokens = usage.getPromptTokens();
            int completionTokens = usage.getCompletionTokens();
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
    public Result<GatewayModelsResponse> listModels() {
        List<GatewayModelView> models = new ArrayList<>();
        List<ModelRegistryConfig.ModelDefinition> registry = registryConfig.getRegistry();

        if (registry != null) {
            var statsMap = gatewayService.getAllStats();
            var healthMap = gatewayService.getAllHealth();
            for (ModelRegistryConfig.ModelDefinition def : registry) {
                models.add(buildModelView(def, statsMap.get(def.getId()), healthMap.get(def.getId())));
            }
        }

        return Result.ok(GatewayModelsResponse.builder()
                .models(models)
                .count(models.size())
                .sceneRoutes(registryConfig.getSceneRoutes() != null ? registryConfig.getSceneRoutes() : Map.of())
                .loadBalanceStrategy(registryConfig.getLoadBalanceStrategy())
                .build());
    }

    @PostMapping("/models/health/probe")
    public Result<GatewayProbeSummaryResponse> probeAllModels() {
        return Result.ok(gatewayService.probeAllModels());
    }

    @PostMapping("/models/{modelId}/health/probe")
    public Result<GatewayModelProbeResponse> probeModel(@PathVariable("modelId") String modelId) {
        return Result.ok(gatewayService.probeModelHealth(modelId));
    }

    @GetMapping("/route-decision")
    public Result<GatewayRouteDecisionResponse> previewRouteDecision(
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
                .routeDecision(RouteDecisionPayload.from(routeDecision))
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

    private GatewayRouteDecisionResponse buildRouteDecisionPreview(ModelGatewayService.RouteDecision decision) {
        List<GatewayCandidateModelView> candidateModels = new ArrayList<>();
        Map<String, ModelGatewayService.ModelStats> statsMap = gatewayService.getAllStats();
        Map<String, ModelGatewayService.ModelHealth> healthMap = gatewayService.getAllHealth();
        for (String candidateId : defaultList(decision.getCandidateModelIds())) {
            ModelRegistryConfig.ModelDefinition definition = gatewayService.getModelDefinition(candidateId);
            var stats = statsMap.get(candidateId);
            var health = healthMap.get(candidateId);
            candidateModels.add(GatewayCandidateModelView.builder()
                    .id(candidateId)
                    .name(definition != null ? definition.getName() : candidateId)
                    .provider(definition != null ? definition.getProvider() : PROVIDER_UNKNOWN)
                    .enabled(definition == null || definition.isEnabled())
                    .healthy(defaultList(decision.getHealthyCandidateModelIds()).contains(candidateId))
                    .selected(candidateId.equals(decision.getSelectedModelId()))
                    .degraded(defaultList(decision.getDegradedModelIds()).contains(candidateId))
                    .weight(definition != null ? definition.getWeight() : 0)
                    .avgLatencyMs(stats != null ? Math.round(stats.getAvgLatencyMs()) : 0L)
                    .successRate(stats != null ? roundPercentage(stats.getSuccessRate()) : DEFAULT_SUCCESS_RATE)
                    .promptCostPer1kTokens(definition != null ? definition.getPromptCostPer1kTokens() : 0d)
                    .completionCostPer1kTokens(definition != null ? definition.getCompletionCostPer1kTokens() : 0d)
                    .reason(buildCandidateReason(candidateId, decision, health != null ? health.getLastReason() : ""))
                    .build());
        }

        ModelRegistryConfig.ModelDefinition selectedDefinition = gatewayService.getModelDefinition(decision.getSelectedModelId());

        return GatewayRouteDecisionResponse.builder()
                .scene(decision.getScene() == null ? "" : decision.getScene())
                .requestedModelId(decision.getRequestedModelId() == null ? "" : decision.getRequestedModelId())
                .selectedModelId(decision.getSelectedModelId() == null ? "" : decision.getSelectedModelId())
                .strategy(decision.getStrategy() == null ? "" : decision.getStrategy())
                .reason(decision.getReason() == null ? "" : decision.getReason())
                .fallbackTriggered(decision.isFallbackTriggered())
                .estimatedCostNote(buildEstimatedCostNote(selectedDefinition))
                .candidateModels(candidateModels)
                .build();
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

    private GatewayStreamEvent buildStreamEvent(String chunk,
                                                boolean done,
                                                String modelId,
                                                ModelGatewayService.RouteDecision routeDecision) {
        return GatewayStreamEvent.builder()
                .chunk(chunk)
                .done(done)
                .model(modelId)
                .routeDecision(routeDecision != null ? RouteDecisionPayload.from(routeDecision) : null)
                .build();
    }

    private GatewayModelView buildModelView(ModelRegistryConfig.ModelDefinition definition,
                                            ModelGatewayService.ModelStats stats,
                                            ModelGatewayService.ModelHealth health) {
        return GatewayModelView.builder()
                .id(definition.getId())
                .name(definition.getName())
                .provider(definition.getProvider())
                .enabled(definition.isEnabled())
                .weight(definition.getWeight())
                .capabilities(definition.getCapabilities())
                .rpmLimit(definition.getRpmLimit())
                .promptCostPer1kTokens(definition.getPromptCostPer1kTokens())
                .completionCostPer1kTokens(definition.getCompletionCostPer1kTokens())
                .healthStatus(health != null ? health.getStatus() : DEFAULT_HEALTH_STATUS)
                .degradedUntil(health != null && health.getDegradedUntil() > 0 ? health.getDegradedUntil() : null)
                .healthReason(health != null ? health.getLastReason() : "")
                .consecutiveFailures(health != null ? health.getConsecutiveFailures().get() : 0)
                .lastCheckedAt(health != null && health.getLastCheckedAt() > 0 ? health.getLastCheckedAt() : null)
                .lastProbeLatencyMs(health != null ? health.getLastProbeLatencyMs() : null)
                .totalCalls(stats != null ? stats.getTotalCalls().get() : 0)
                .successCalls(stats != null ? stats.getSuccessCalls().get() : 0)
                .avgLatencyMs(stats != null ? (double) Math.round(stats.getAvgLatencyMs()) : 0d)
                .successRate(stats != null ? roundPercentage(stats.getSuccessRate()) : DEFAULT_SUCCESS_RATE)
                .totalPromptTokens(stats != null ? stats.getTotalPromptTokens() : 0L)
                .totalCompletionTokens(stats != null ? stats.getTotalCompletionTokens() : 0L)
                .totalEstimatedCost(stats != null ? roundCost(stats.getTotalEstimatedCost()) : 0d)
                .build();
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
}
