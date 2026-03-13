package com.example.aiplatform.controller;

import com.example.aiplatform.dto.InferenceRequest;
import com.example.aiplatform.dto.InferenceResponse;
import com.example.aiplatform.dto.ModelRegistrationRequest;
import com.example.aiplatform.gateway.ModelGatewayService;
import com.example.aiplatform.model.ModelProfile;
import com.example.aiplatform.monitoring.MonitoringService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/models")
public class ModelGatewayController {

    private final ModelGatewayService modelGatewayService;
    private final MonitoringService monitoringService;

    public ModelGatewayController(ModelGatewayService modelGatewayService, MonitoringService monitoringService) {
        this.modelGatewayService = modelGatewayService;
        this.monitoringService = monitoringService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ModelProfile register(@Valid @RequestBody ModelRegistrationRequest request) {
        return modelGatewayService.register(request);
    }

    @GetMapping
    public List<ModelProfile> list() {
        return modelGatewayService.listModels();
    }

    @PostMapping("/inference")
    public InferenceResponse inference(@Valid @RequestBody InferenceRequest request) {
        InferenceResponse response = modelGatewayService.routeInference(request);
        monitoringService.recordRequest(response.latencyMs(), false, response.response().length(), false);
        return response;
    }
}
