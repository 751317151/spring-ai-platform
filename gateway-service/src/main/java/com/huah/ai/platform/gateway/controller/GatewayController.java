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

/**
 * 统一 AI 网关控制器
 * 提供 OpenAI 兼容接口，支持普通请求和流式输出
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class GatewayController {

    private final ModelGatewayService gatewayService;
    private final ModelRegistryConfig registryConfig;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 普通对话接口
     */
    @PostMapping("/completions")
    @CircuitBreaker(name = "aiGateway", fallbackMethod = "chatFallback")
    @RateLimiter(name = "aiGateway")
    public Result<ChatResponse> chat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Scene", defaultValue = "default") String scene,
            @RequestHeader(value = "X-Model-Id", required = false) String modelId) {

        long start = System.currentTimeMillis();
        String usedModelId = modelId != null ? modelId : "auto";

        try {
            ChatClient client = modelId != null
                    ? gatewayService.getChatClientById(modelId)
                    : gatewayService.getChatClient(scene);

            List<Message> messages = buildMessages(request);

            String content = client.prompt()
                    .messages(messages)
                    .call()
                    .content();

            long latency = System.currentTimeMillis() - start;
            gatewayService.recordCall(usedModelId, latency, true);

            return Result.ok(ChatResponse.builder()
                    .content(content)
                    .model(usedModelId)
                    .latencyMs(latency)
                    .build());

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            gatewayService.recordCall(usedModelId, latency, false);
            throw e;
        }
    }

    /**
     * 流式对话接口（SSE）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Scene", defaultValue = "default") String scene,
            @RequestHeader(value = "X-Model-Id", required = false) String modelId) {

        SseEmitter emitter = new SseEmitter(60_000L);
        ChatClient client = modelId != null
                ? gatewayService.getChatClientById(modelId)
                : gatewayService.getChatClient(scene);

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
                                        .data(Map.of("chunk", chunk, "done", false)));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnComplete(() -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .data(Map.of("chunk", "", "done", true)));
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnError(emitter::completeWithError)
                        .subscribe();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 获取模型列表（注册表 + 运行时统计）
     */
    @GetMapping("/models")
    public Result<Map<String, Object>> listModels() {
        Map<String, Object> result = new HashMap<>();

        // 模型注册信息
        List<Map<String, Object>> models = new ArrayList<>();
        if (registryConfig.getRegistry() != null) {
            var statsMap = gatewayService.getAllStats();
            for (ModelRegistryConfig.ModelDefinition def : registryConfig.getRegistry()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", def.getId());
                m.put("name", def.getName());
                m.put("provider", def.getProvider());
                m.put("enabled", def.isEnabled());
                m.put("weight", def.getWeight());
                m.put("capabilities", def.getCapabilities());
                m.put("rpmLimit", def.getRpmLimit());

                var stats = statsMap.get(def.getId());
                if (stats != null) {
                    m.put("totalCalls", stats.getTotalCalls().get());
                    m.put("successCalls", stats.getSuccessCalls().get());
                    m.put("avgLatencyMs", Math.round(stats.getAvgLatencyMs()));
                    m.put("successRate", Math.round(stats.getSuccessRate() * 1000) / 10.0);
                } else {
                    m.put("totalCalls", 0);
                    m.put("successCalls", 0);
                    m.put("avgLatencyMs", 0);
                    m.put("successRate", 100.0);
                }
                models.add(m);
            }
        }
        result.put("models", models);
        result.put("count", models.size());

        // 场景路由规则
        result.put("sceneRoutes", registryConfig.getSceneRoutes() != null
                ? registryConfig.getSceneRoutes() : Map.of());

        // 负载均衡策略
        result.put("loadBalanceStrategy", registryConfig.getLoadBalanceStrategy());

        return Result.ok(result);
    }

    /**
     * 熔断降级方法
     */
    public Result<ChatResponse> chatFallback(ChatRequest request, String scene, String modelId, Exception e) {
        log.error("AI网关熔断降级，模型: {}, 错误: {}", modelId, e.getMessage());
        return Result.fail(503, "AI服务暂时不可用，已触发熔断保护，请稍后重试");
    }

    private List<Message> buildMessages(ChatRequest request) {
        List<Message> messages = new ArrayList<>();
        if (request.getHistory() != null) {
            for (ChatRequest.HistoryMessage h : request.getHistory()) {
                if ("user".equals(h.getRole())) {
                    messages.add(new UserMessage(h.getContent()));
                } else if ("assistant".equals(h.getRole())) {
                    messages.add(new AssistantMessage(h.getContent()));
                }
            }
        }
        messages.add(new UserMessage(request.getMessage()));
        return messages;
    }
}
