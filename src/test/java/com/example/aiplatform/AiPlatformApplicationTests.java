package com.example.aiplatform;

import com.example.aiplatform.dto.InferenceRequest;
import com.example.aiplatform.dto.ModelRegistrationRequest;
import com.example.aiplatform.gateway.ModelGatewayService;
import com.example.aiplatform.model.ModelProviderType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

@SpringBootTest
class AiPlatformApplicationTests {

    @Autowired
    private ModelGatewayService modelGatewayService;

    @Test
    void shouldRegisterAndRouteModel() {
        modelGatewayService.register(new ModelRegistrationRequest(
                "self-hosted-model",
                "Self Hosted",
                ModelProviderType.SELF_HOSTED,
                "http://localhost:8000/v1",
                "demo",
                Map.of("cluster", "onprem")
        ));

        var response = modelGatewayService.routeInference(
                new InferenceRequest("请总结生产异常原因", "self-hosted-model", Map.of(), true)
        );

        Assertions.assertEquals("self-hosted-model", response.modelId());
        Assertions.assertTrue(response.response().contains("请总结生产异常原因"));
    }
}
