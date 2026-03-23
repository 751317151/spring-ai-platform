package com.huah.ai.platform.gateway.service;

import com.huah.ai.platform.gateway.config.ModelRegistryConfig;
import com.huah.ai.platform.gateway.config.ModelRegistryConfig.ModelDefinition;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ModelGatewayServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ModelGatewayService service;

    @BeforeEach
    void setUp() throws Exception {
        ModelRegistryConfig config = new ModelRegistryConfig();
        config.setLoadBalanceStrategy("round-robin");
        config.setSceneRoutes(Map.of(
                "code", List.of("deepseek-chat", "gpt-4o"),
                "analysis", List.of("gpt-4o")
        ));
        config.setRegistry(List.of(
                definition("deepseek-chat", 2),
                definition("gpt-4o", 1),
                definition("qwen-plus", 1)
        ));

        service = new ModelGatewayService(config, jdbcTemplate, new SimpleMeterRegistry());
        putModel(service, "deepseek-chat");
        putModel(service, "gpt-4o");
        putModel(service, "qwen-plus");
    }

    @Test
    void selectModelUsesSceneRouteWithRoundRobin() {
        assertEquals("deepseek-chat", service.selectModel("code"));
        assertEquals("gpt-4o", service.selectModel("code"));
        assertEquals("deepseek-chat", service.selectModel("code"));
    }

    @Test
    void selectModelFallsBackToAllModelsWhenSceneMissing() {
        assertEquals("deepseek-chat", service.selectModel("unknown-scene"));
        assertEquals("gpt-4o", service.selectModel("unknown-scene"));
        assertEquals("qwen-plus", service.selectModel("unknown-scene"));
    }

    @Test
    void selectModelUsesLeastLatencyWhenConfigured() throws Exception {
        ModelRegistryConfig config = new ModelRegistryConfig();
        config.setLoadBalanceStrategy("least-latency");
        config.setRegistry(List.of(
                definition("model-a", 1),
                definition("model-b", 1)
        ));

        ModelGatewayService leastLatencyService =
                new ModelGatewayService(config, jdbcTemplate, new SimpleMeterRegistry());
        putModel(leastLatencyService, "model-a");
        putModel(leastLatencyService, "model-b");
        putStats(leastLatencyService, "model-a", 4, 4, 400);
        putStats(leastLatencyService, "model-b", 4, 4, 80);

        assertEquals("model-b", leastLatencyService.selectModel(null));
    }

    private static ModelDefinition definition(String id, int weight) {
        ModelDefinition definition = new ModelDefinition();
        definition.setId(id);
        definition.setName(id);
        definition.setProvider("ollama");
        definition.setBaseUrl("http://localhost:11434");
        definition.setWeight(weight);
        definition.setEnabled(true);
        return definition;
    }

    @SuppressWarnings("unchecked")
    private static void putModel(ModelGatewayService target, String id) throws Exception {
        Field field = ModelGatewayService.class.getDeclaredField("modelCache");
        field.setAccessible(true);
        Map<String, ChatModel> modelCache = (Map<String, ChatModel>) field.get(target);
        modelCache.put(id, mock(ChatModel.class));
    }

    @SuppressWarnings("unchecked")
    private static void putStats(ModelGatewayService target, String id, int total, int success, long totalLatency)
            throws Exception {
        Field field = ModelGatewayService.class.getDeclaredField("statsMap");
        field.setAccessible(true);
        Map<String, ModelGatewayService.ModelStats> statsMap =
                (Map<String, ModelGatewayService.ModelStats>) field.get(target);
        ModelGatewayService.ModelStats stats = new ModelGatewayService.ModelStats(id);
        stats.restore(total, success, totalLatency);
        statsMap.put(id, stats);
    }
}
