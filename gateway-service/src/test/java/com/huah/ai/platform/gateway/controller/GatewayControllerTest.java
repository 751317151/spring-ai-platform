package com.huah.ai.platform.gateway.controller;

import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.gateway.model.ChatRequest;
import com.huah.ai.platform.gateway.model.ChatResponse;
import com.huah.ai.platform.gateway.model.GatewayModelsResponse;
import com.huah.ai.platform.gateway.model.GatewayRouteDecisionResponse;
import com.huah.ai.platform.gateway.service.GatewayFacadeService;
import com.huah.ai.platform.gateway.service.ModelGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayControllerTest {

    private ModelGatewayService gatewayService;
    private GatewayFacadeService gatewayFacadeService;
    private GatewayController controller;

    @BeforeEach
    void setUp() {
        gatewayService = mock(ModelGatewayService.class);
        gatewayFacadeService = mock(GatewayFacadeService.class);
        controller = new GatewayController(gatewayService, gatewayFacadeService);
    }

    @Test
    void updateLoadBalanceShouldRejectBlankStrategy() {
        Result<String> result = controller.updateLoadBalance(Map.of());

        assertEquals(400, result.getCode());
        assertEquals("strategy must not be blank", result.getMessage());
    }

    @Test
    void updateLoadBalanceShouldRejectUnsupportedStrategy() {
        when(gatewayFacadeService.isSupportedStrategy("random")).thenReturn(false);

        Result<String> result = controller.updateLoadBalance(Map.of("strategy", "random"));

        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("Unsupported strategy"));
    }

    @Test
    void updateLoadBalanceShouldDelegateToFacade() {
        when(gatewayFacadeService.isSupportedStrategy("weighted")).thenReturn(true);
        when(gatewayFacadeService.updateLoadBalance("weighted"))
                .thenReturn("Load balance strategy updated to: weighted");

        Result<String> result = controller.updateLoadBalance(Map.of("strategy", "weighted"));

        assertEquals(200, result.getCode());
        assertEquals("Load balance strategy updated to: weighted", result.getData());
        verify(gatewayFacadeService).updateLoadBalance("weighted");
    }

    @Test
    void chatFallbackShouldReturnServiceUnavailableResult() {
        Result<ChatResponse> result =
                controller.chatFallback(new ChatRequest(), "default", "model-a", new RuntimeException("boom"));

        assertEquals(503, result.getCode());
        assertEquals(
                "AI service is temporarily unavailable because circuit breaker protection was triggered. Please retry later.",
                result.getMessage());
    }

    @Test
    void listModelsShouldDelegateToFacade() {
        GatewayModelsResponse response =
                GatewayModelsResponse.builder().count(1).build();
        when(gatewayFacadeService.listModels()).thenReturn(response);

        Result<GatewayModelsResponse> result = controller.listModels();

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().getCount());
    }

    @Test
    void previewRouteDecisionShouldDelegateToFacade() {
        GatewayRouteDecisionResponse response =
                GatewayRouteDecisionResponse.builder().selectedModelId("model-a").build();
        when(gatewayFacadeService.previewRouteDecision("default", null)).thenReturn(response);

        Result<GatewayRouteDecisionResponse> result = controller.previewRouteDecision("default", null);

        assertEquals(200, result.getCode());
        assertEquals("model-a", result.getData().getSelectedModelId());
    }
}
