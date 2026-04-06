package com.huah.ai.platform.gateway.controller;

import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.gateway.model.ChatRequest;
import com.huah.ai.platform.gateway.model.ChatResponse;
import com.huah.ai.platform.gateway.model.GatewayModelProbeResponse;
import com.huah.ai.platform.gateway.model.GatewayModelsResponse;
import com.huah.ai.platform.gateway.model.GatewayProbeSummaryResponse;
import com.huah.ai.platform.gateway.model.GatewayRouteDecisionResponse;
import com.huah.ai.platform.gateway.service.GatewayFacadeService;
import com.huah.ai.platform.gateway.service.ModelGatewayService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class GatewayController {

    private static final String DEFAULT_SCENE = "default";
    private static final String HEADER_SCENE = "X-Scene";
    private static final String HEADER_MODEL_ID = "X-Model-Id";
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_SERVICE_UNAVAILABLE = 503;
    private static final String MESSAGE_SERVICE_UNAVAILABLE =
            "AI service is temporarily unavailable because circuit breaker protection was triggered. Please retry later.";
    private static final String MESSAGE_STRATEGY_REQUIRED = "strategy must not be blank";
    private static final String MESSAGE_UNSUPPORTED_STRATEGY =
            "Unsupported strategy: %s. Supported values: round-robin, weighted, least-latency";

    private final ModelGatewayService gatewayService;
    private final GatewayFacadeService gatewayFacadeService;

    @PostMapping("/completions")
    @CircuitBreaker(name = "aiGateway", fallbackMethod = "chatFallback")
    @RateLimiter(name = "aiGateway")
    public Result<ChatResponse> chat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = HEADER_SCENE, defaultValue = DEFAULT_SCENE) String scene,
            @RequestHeader(value = HEADER_MODEL_ID, required = false) String modelId) {
        return Result.ok(gatewayFacadeService.chat(request, scene, modelId));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = HEADER_SCENE, defaultValue = DEFAULT_SCENE) String scene,
            @RequestHeader(value = HEADER_MODEL_ID, required = false) String modelId) {
        return gatewayFacadeService.streamChat(request, scene, modelId);
    }

    @GetMapping("/models")
    public Result<GatewayModelsResponse> listModels() {
        return Result.ok(gatewayFacadeService.listModels());
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
        return Result.ok(gatewayFacadeService.previewRouteDecision(scene, requestedModelId));
    }

    public Result<ChatResponse> chatFallback(ChatRequest request, String scene, String modelId, Exception ex) {
        log.error("AI gateway fallback triggered, modelId={}, error={}", modelId, ex.getMessage());
        return Result.fail(HTTP_SERVICE_UNAVAILABLE, MESSAGE_SERVICE_UNAVAILABLE);
    }

    @PutMapping("/config/load-balance")
    public Result<String> updateLoadBalance(@RequestBody Map<String, String> body) {
        String strategy = body.get("strategy");
        if (strategy == null || strategy.isBlank()) {
            return Result.fail(HTTP_BAD_REQUEST, MESSAGE_STRATEGY_REQUIRED);
        }
        if (!gatewayFacadeService.isSupportedStrategy(strategy)) {
            return Result.fail(HTTP_BAD_REQUEST, MESSAGE_UNSUPPORTED_STRATEGY.formatted(strategy));
        }
        log.info("Load balance strategy updated to: {}", strategy);
        return Result.ok(gatewayFacadeService.updateLoadBalance(strategy));
    }
}
