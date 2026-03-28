package com.huah.ai.platform.gateway.service;

import com.huah.ai.platform.gateway.config.ModelRegistryConfig;
import com.huah.ai.platform.gateway.model.GatewayModelProbeResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelGatewayServiceTest {

    @Test
    void selectModelShouldPreferHealthyCandidateWhenAnotherModelIsDegraded() {
        ModelRegistryConfig config = new ModelRegistryConfig();
        config.setLoadBalanceStrategy("round-robin");
        config.setSceneRoutes(Map.of("default", List.of("model-a", "model-b")));

        ModelRegistryConfig.ModelDefinition a = new ModelRegistryConfig.ModelDefinition();
        a.setId("model-a");
        a.setEnabled(true);
        ModelRegistryConfig.ModelDefinition b = new ModelRegistryConfig.ModelDefinition();
        b.setId("model-b");
        b.setEnabled(true);
        config.setRegistry(List.of(a, b));

        ModelGatewayService service = new ModelGatewayService(config, mock(JdbcTemplate.class), new SimpleMeterRegistry());

        @SuppressWarnings("unchecked")
        Map<String, ChatModel> modelCache = (Map<String, ChatModel>) ReflectionTestUtils.getField(service, "modelCache");
        modelCache.put("model-a", mock(ChatModel.class));
        modelCache.put("model-b", mock(ChatModel.class));

        service.recordCall("model-a", 120, false);
        service.recordCall("model-a", 140, false);
        service.recordCall("model-a", 160, false);

        String selected = service.selectModel("default");

        assertEquals("model-b", selected);
        assertEquals("degraded", service.getAllHealth().get("model-a").getStatus());
        assertTrue(service.getAllHealth().get("model-a").getDegradedUntil() > System.currentTimeMillis());
    }

    @Test
    void selectModelWithDecisionShouldExplainFallbackWhenSceneCandidatesAreDegraded() {
        ModelRegistryConfig config = new ModelRegistryConfig();
        config.setLoadBalanceStrategy("round-robin");
        config.setSceneRoutes(Map.of("chat", List.of("model-a")));

        ModelRegistryConfig.ModelDefinition a = new ModelRegistryConfig.ModelDefinition();
        a.setId("model-a");
        a.setEnabled(true);
        config.setRegistry(List.of(a));

        ModelGatewayService service = new ModelGatewayService(config, mock(JdbcTemplate.class), new SimpleMeterRegistry());

        @SuppressWarnings("unchecked")
        Map<String, ChatModel> modelCache = (Map<String, ChatModel>) ReflectionTestUtils.getField(service, "modelCache");
        modelCache.put("model-a", mock(ChatModel.class));

        service.recordCall("model-a", 100, false);
        service.recordCall("model-a", 100, false);
        service.recordCall("model-a", 100, false);

        ModelGatewayService.RouteDecision decision = service.selectModelWithDecision("chat", null);

        assertEquals("model-a", decision.getSelectedModelId());
        assertTrue(decision.isFallbackTriggered());
        assertTrue(decision.getReason().toLowerCase().contains("fallback"));
        assertEquals(List.of("model-a"), decision.getDegradedModelIds());
    }

    @Test
    void selectModelWithDecisionShouldExplainManualOverride() {
        ModelRegistryConfig config = new ModelRegistryConfig();
        config.setLoadBalanceStrategy("weighted");

        ModelRegistryConfig.ModelDefinition a = new ModelRegistryConfig.ModelDefinition();
        a.setId("model-a");
        a.setEnabled(true);
        config.setRegistry(List.of(a));

        ModelGatewayService service = new ModelGatewayService(config, mock(JdbcTemplate.class), new SimpleMeterRegistry());

        @SuppressWarnings("unchecked")
        Map<String, ChatModel> modelCache = (Map<String, ChatModel>) ReflectionTestUtils.getField(service, "modelCache");
        modelCache.put("model-a", mock(ChatModel.class));

        ModelGatewayService.RouteDecision decision = service.selectModelWithDecision("default", "model-a");

        assertEquals("model-a", decision.getSelectedModelId());
        assertEquals("manual", decision.getStrategy());
        assertTrue(decision.getReason().contains("Matched explicit model selection"));
    }

    @Test
    void probeModelHealthShouldUpdateHealthStatusWithoutAffectingStats() {
        ModelRegistryConfig config = new ModelRegistryConfig();
        ModelRegistryConfig.ModelDefinition a = new ModelRegistryConfig.ModelDefinition();
        a.setId("model-a");
        a.setEnabled(true);
        config.setRegistry(List.of(a));

        ModelGatewayService service = new ModelGatewayService(config, mock(JdbcTemplate.class), new SimpleMeterRegistry());

        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(mock(ChatResponse.class));

        @SuppressWarnings("unchecked")
        Map<String, ChatModel> modelCache = (Map<String, ChatModel>) ReflectionTestUtils.getField(service, "modelCache");
        modelCache.put("model-a", chatModel);

        GatewayModelProbeResponse result = service.probeModelHealth("model-a");

        assertEquals("healthy", result.getStatus());
        assertEquals(0, service.getAllStats().getOrDefault("model-a", new ModelGatewayService.ModelStats("model-a")).getTotalCalls().get());
        assertTrue(service.getAllHealth().get("model-a").getLastCheckedAt() > 0);
    }

    @Test
    void recordCallShouldAccumulateTokenUsageAndEstimatedCost() {
        ModelRegistryConfig config = new ModelRegistryConfig();
        ModelRegistryConfig.ModelDefinition a = new ModelRegistryConfig.ModelDefinition();
        a.setId("model-a");
        a.setEnabled(true);
        a.setPromptCostPer1kTokens(0.01);
        a.setCompletionCostPer1kTokens(0.02);
        config.setRegistry(List.of(a));

        ModelGatewayService service = new ModelGatewayService(config, mock(JdbcTemplate.class), new SimpleMeterRegistry());
        service.recordCall("model-a", 100, true, 500, 250);

        ModelGatewayService.ModelStats stats = service.getAllStats().get("model-a");
        assertEquals(500, stats.getTotalPromptTokens());
        assertEquals(250, stats.getTotalCompletionTokens());
        assertTrue(stats.getTotalEstimatedCost() > 0d);
    }
}
