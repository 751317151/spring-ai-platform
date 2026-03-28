package com.huah.ai.platform.gateway.controller;

import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.gateway.config.ModelRegistryConfig;
import com.huah.ai.platform.gateway.model.ChatRequest;
import com.huah.ai.platform.gateway.model.ChatResponse;
import com.huah.ai.platform.gateway.service.ModelGatewayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayControllerTest {

    private ModelGatewayService gatewayService;
    private ModelRegistryConfig registryConfig;
    private GatewayController controller;

    @BeforeEach
    void setUp() {
        gatewayService = mock(ModelGatewayService.class);
        registryConfig = new ModelRegistryConfig();
        controller = new GatewayController(gatewayService, registryConfig);
    }

    @AfterEach
    void tearDown() {
        controller.shutdownExecutor();
    }

    @Test
    void updateLoadBalanceShouldRejectBlankStrategy() {
        Result<String> result = controller.updateLoadBalance(Map.of());

        assertEquals(400, result.getCode());
        assertEquals("strategy must not be blank", result.getMessage());
    }

    @Test
    void updateLoadBalanceShouldRejectUnsupportedStrategy() {
        Result<String> result = controller.updateLoadBalance(Map.of("strategy", "random"));

        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("Unsupported strategy"));
    }

    @Test
    void chatFallbackShouldReturnServiceUnavailableResult() {
        Result<ChatResponse> result = controller.chatFallback(new ChatRequest(), "default", "model-a", new RuntimeException("boom"));

        assertEquals(503, result.getCode());
        assertEquals("AI service is temporarily unavailable because circuit breaker protection was triggered. Please retry later.", result.getMessage());
    }

    @Test
    void listModelsShouldIncludeStatsAndHealth() {
        ModelRegistryConfig.ModelDefinition definition = new ModelRegistryConfig.ModelDefinition();
        definition.setId("model-a");
        definition.setName("GPT");
        definition.setProvider("openai");
        definition.setEnabled(true);
        definition.setWeight(2);
        definition.setRpmLimit(100);
        definition.setPromptCostPer1kTokens(0.01);
        definition.setCompletionCostPer1kTokens(0.02);
        registryConfig.setRegistry(List.of(definition));
        registryConfig.setSceneRoutes(Map.of("default", List.of("model-a")));
        registryConfig.setLoadBalanceStrategy("weighted");

        ModelGatewayService.ModelStats stats = new ModelGatewayService.ModelStats("model-a");
        stats.record(120, true, 500, 250, 0.015d);

        ModelGatewayService.ModelHealth health = new ModelGatewayService.ModelHealth("model-a");
        health.recordProbe(true, 80L, "probe-ok");

        when(gatewayService.getAllStats()).thenReturn(Map.of("model-a", stats));
        when(gatewayService.getAllHealth()).thenReturn(Map.of("model-a", health));

        Result<Map<String, Object>> result = controller.listModels();

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().get("count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> models = (List<Map<String, Object>>) result.getData().get("models");
        assertEquals("model-a", models.get(0).get("id"));
        assertEquals("healthy", models.get(0).get("healthStatus"));
        assertEquals(100.0d, models.get(0).get("successRate"));
    }

    @Test
    void previewRouteDecisionShouldReturnCandidateModels() {
        ModelGatewayService.RouteDecision decision = ModelGatewayService.RouteDecision.builder()
                .scene("default")
                .selectedModelId("model-a")
                .strategy("weighted")
                .reason("selected")
                .candidateModelIds(List.of("model-a"))
                .healthyCandidateModelIds(List.of("model-a"))
                .degradedModelIds(List.of())
                .fallbackTriggered(false)
                .build();

        ModelRegistryConfig.ModelDefinition definition = new ModelRegistryConfig.ModelDefinition();
        definition.setId("model-a");
        definition.setName("GPT");
        definition.setProvider("openai");
        definition.setEnabled(true);
        definition.setWeight(3);
        definition.setPromptCostPer1kTokens(0.01);
        definition.setCompletionCostPer1kTokens(0.02);

        when(gatewayService.selectModelWithDecision("default", null)).thenReturn(decision);
        when(gatewayService.getModelDefinition("model-a")).thenReturn(definition);
        when(gatewayService.getAllStats()).thenReturn(Map.of());
        when(gatewayService.getAllHealth()).thenReturn(Map.of());

        Result<Map<String, Object>> result = controller.previewRouteDecision("default", null);

        assertEquals(200, result.getCode());
        assertEquals("model-a", result.getData().get("selectedModelId"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidateModels = (List<Map<String, Object>>) result.getData().get("candidateModels");
        assertEquals(1, candidateModels.size());
        assertEquals(true, candidateModels.get(0).get("selected"));
    }
}
