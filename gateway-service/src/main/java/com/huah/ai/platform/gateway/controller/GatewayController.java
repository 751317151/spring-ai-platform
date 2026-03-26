package com.huah.ai.platform.gateway.controller;

import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.gateway.config.ModelRegistryConfig;
import com.huah.ai.platform.gateway.model.ChatRequest;
import com.huah.ai.platform.gateway.model.ChatResponse;
import com.huah.ai.platform.gateway.service.ModelGatewayService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class GatewayController {

    private final ModelGatewayService gatewayService;
    private final ModelRegistryConfig registryConfig;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @PostMapping("/completions")
    @CircuitBreaker(name = "aiGateway", fallbackMethod = "chatFallback")
    @RateLimiter(name = "aiGateway")
    public Result<ChatResponse> chat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Scene", defaultValue = "default") String scene,
            @RequestHeader(value = "X-Model-Id", required = false) String modelId) {

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

            return Result.ok(ChatResponse.builder()
                    .content(content)
                    .model(usedModelId)
                    .latencyMs(latency)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .estimatedCost(calculateEstimatedCost(routeDecision, promptTokens, completionTokens))
                    .routeDecision(routeDecision.toMap())
                    .build());
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            gatewayService.recordCall(usedModelId, latency, false, 0, 0);
            throw e;
        }
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Scene", defaultValue = "default") String scene,
            @RequestHeader(value = "X-Model-Id", required = false) String modelId) {

        SseEmitter emitter = new SseEmitter(60_000L);
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
                                emitter.send(SseEmitter.event()
                                        .data(Map.of("chunk", chunk, "done", false, "model", usedModelId)));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnComplete(() -> {
                            long latency = System.currentTimeMillis() - startTime;
                            gatewayService.recordCall(usedModelId, latency, true, 0, 0);
                            try {
                                emitter.send(SseEmitter.event().data(Map.of(
                                        "chunk", "",
                                        "done", true,
                                        "model", usedModelId,
                                        "routeDecision", routeDecision.toMap()
                                )));
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
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> models = new ArrayList<>();

        if (registryConfig.getRegistry() != null) {
            var statsMap = gatewayService.getAllStats();
            var healthMap = gatewayService.getAllHealth();
            for (ModelRegistryConfig.ModelDefinition def : registryConfig.getRegistry()) {
                Map<String, Object> model = new HashMap<>();
                model.put("id", def.getId());
                model.put("name", def.getName());
                model.put("provider", def.getProvider());
                model.put("enabled", def.isEnabled());
                model.put("weight", def.getWeight());
                model.put("capabilities", def.getCapabilities());
                model.put("rpmLimit", def.getRpmLimit());
                model.put("promptCostPer1kTokens", def.getPromptCostPer1kTokens());
                model.put("completionCostPer1kTokens", def.getCompletionCostPer1kTokens());

                var health = healthMap.get(def.getId());
                model.put("healthStatus", health != null ? health.getStatus() : "healthy");
                model.put("degradedUntil", health != null && health.getDegradedUntil() > 0 ? health.getDegradedUntil() : null);
                model.put("healthReason", health != null ? health.getLastReason() : "");
                model.put("consecutiveFailures", health != null ? health.getConsecutiveFailures().get() : 0);
                model.put("lastCheckedAt", health != null && health.getLastCheckedAt() > 0 ? health.getLastCheckedAt() : null);
                model.put("lastProbeLatencyMs", health != null ? health.getLastProbeLatencyMs() : null);

                var stats = statsMap.get(def.getId());
                if (stats != null) {
                    model.put("totalCalls", stats.getTotalCalls().get());
                    model.put("successCalls", stats.getSuccessCalls().get());
                    model.put("avgLatencyMs", Math.round(stats.getAvgLatencyMs()));
                    model.put("successRate", Math.round(stats.getSuccessRate() * 1000) / 10.0);
                    model.put("totalPromptTokens", stats.getTotalPromptTokens());
                    model.put("totalCompletionTokens", stats.getTotalCompletionTokens());
                    model.put("totalEstimatedCost", Math.round(stats.getTotalEstimatedCost() * 100000d) / 100000d);
                } else {
                    model.put("totalCalls", 0);
                    model.put("successCalls", 0);
                    model.put("avgLatencyMs", 0);
                    model.put("successRate", 100.0);
                    model.put("totalPromptTokens", 0);
                    model.put("totalCompletionTokens", 0);
                    model.put("totalEstimatedCost", 0);
                }
                models.add(model);
            }
        }

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
            @RequestParam(name = "scene", defaultValue = "default") String scene,
            @RequestParam(name = "requestedModelId", required = false) String requestedModelId) {
        ModelGatewayService.RouteDecision decision = gatewayService.selectModelWithDecision(scene, requestedModelId);
        return Result.ok(buildRouteDecisionPreview(decision));
    }

    public Result<ChatResponse> chatFallback(ChatRequest request, String scene, String modelId, Exception e) {
        log.error("AI 网关触发熔断降级，模型: {}, 错误: {}", modelId, e.getMessage());
        return Result.fail(503, "AI 服务暂时不可用，已触发熔断保护，请稍后重试");
    }

    @PutMapping("/config/load-balance")
    public Result<String> updateLoadBalance(@RequestBody Map<String, String> body) {
        String strategy = body.get("strategy");
        if (strategy == null || strategy.isBlank()) {
            return Result.fail(400, "strategy 不能为空");
        }
        if (!"round-robin".equals(strategy) && !"weighted".equals(strategy) && !"least-latency".equals(strategy)) {
            return Result.fail(400, "不支持的策略: " + strategy + "，可选: round-robin, weighted, least-latency");
        }
        registryConfig.setLoadBalanceStrategy(strategy);
        log.info("负载均衡策略已更新为: {}", strategy);
        return Result.ok("负载均衡策略已更新为: " + strategy);
    }

    private List<Message> buildMessages(ChatRequest request) {
        List<Message> messages = new ArrayList<>();
        if (request.getHistory() != null) {
            for (ChatRequest.HistoryMessage history : request.getHistory()) {
                if ("user".equals(history.getRole())) {
                    messages.add(new UserMessage(history.getContent()));
                } else if ("assistant".equals(history.getRole())) {
                    messages.add(new AssistantMessage(history.getContent()));
                }
            }
        }
        messages.add(new UserMessage(request.getMessage()));
        return messages;
    }

    private double calculateEstimatedCost(ModelGatewayService.RouteDecision routeDecision, int promptTokens, int completionTokens) {
        String selectedModelId = routeDecision.getSelectedModelId();
        ModelRegistryConfig.ModelDefinition definition = registryConfig.getRegistry() == null
                ? null
                : registryConfig.getRegistry().stream()
                .filter(item -> selectedModelId.equals(item.getId()))
                .findFirst()
                .orElse(null);
        if (definition == null) {
            return 0d;
        }
        double promptCost = (promptTokens / 1000.0d) * definition.getPromptCostPer1kTokens();
        double completionCost = (completionTokens / 1000.0d) * definition.getCompletionCostPer1kTokens();
        return Math.round((promptCost + completionCost) * 100000d) / 100000d;
    }

    private Map<String, Object> buildRouteDecisionPreview(ModelGatewayService.RouteDecision decision) {
        List<Map<String, Object>> candidateModels = new ArrayList<>();
        for (String candidateId : decision.getCandidateModelIds() == null ? List.<String>of() : decision.getCandidateModelIds()) {
            ModelRegistryConfig.ModelDefinition definition = gatewayService.getModelDefinition(candidateId);
            var stats = gatewayService.getAllStats().get(candidateId);
            var health = gatewayService.getAllHealth().get(candidateId);
            Map<String, Object> item = new HashMap<>();
            item.put("id", candidateId);
            item.put("name", definition != null ? definition.getName() : candidateId);
            item.put("provider", definition != null ? definition.getProvider() : "unknown");
            item.put("enabled", definition == null || definition.isEnabled());
            item.put("healthy", decision.getHealthyCandidateModelIds() != null && decision.getHealthyCandidateModelIds().contains(candidateId));
            item.put("selected", candidateId.equals(decision.getSelectedModelId()));
            item.put("degraded", decision.getDegradedModelIds() != null && decision.getDegradedModelIds().contains(candidateId));
            item.put("weight", definition != null ? definition.getWeight() : 0);
            item.put("avgLatencyMs", stats != null ? Math.round(stats.getAvgLatencyMs()) : 0);
            item.put("successRate", stats != null ? Math.round(stats.getSuccessRate() * 1000d) / 10d : 100d);
            item.put("promptCostPer1kTokens", definition != null ? definition.getPromptCostPer1kTokens() : 0d);
            item.put("completionCostPer1kTokens", definition != null ? definition.getCompletionCostPer1kTokens() : 0d);
            item.put("reason", buildCandidateReason(candidateId, decision, health != null ? health.getLastReason() : ""));
            candidateModels.add(item);
        }

        ModelRegistryConfig.ModelDefinition selectedDefinition = gatewayService.getModelDefinition(decision.getSelectedModelId());
        String estimatedCostNote = selectedDefinition == null
                ? ""
                : "按每 1K Token 估算，输入约 " + selectedDefinition.getPromptCostPer1kTokens()
                + "，输出约 " + selectedDefinition.getCompletionCostPer1kTokens();

        return Map.of(
                "scene", decision.getScene() == null ? "" : decision.getScene(),
                "requestedModelId", decision.getRequestedModelId() == null ? "" : decision.getRequestedModelId(),
                "selectedModelId", decision.getSelectedModelId() == null ? "" : decision.getSelectedModelId(),
                "strategy", decision.getStrategy() == null ? "" : decision.getStrategy(),
                "reason", decision.getReason() == null ? "" : decision.getReason(),
                "fallbackTriggered", decision.isFallbackTriggered(),
                "estimatedCostNote", estimatedCostNote,
                "candidateModels", candidateModels
        );
    }

    private String buildCandidateReason(String candidateId, ModelGatewayService.RouteDecision decision, String healthReason) {
        if (candidateId.equals(decision.getSelectedModelId())) {
            return "当前场景最终选中的模型";
        }
        if (decision.getDegradedModelIds() != null && decision.getDegradedModelIds().contains(candidateId)) {
            return healthReason == null || healthReason.isBlank() ? "模型当前处于降级状态" : healthReason;
        }
        if (decision.getHealthyCandidateModelIds() != null && !decision.getHealthyCandidateModelIds().contains(candidateId)) {
            return "未进入健康候选集";
        }
        return "进入候选池，但本次未被负载策略选中";
    }
}
