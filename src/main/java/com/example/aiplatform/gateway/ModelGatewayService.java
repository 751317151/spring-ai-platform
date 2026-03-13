package com.example.aiplatform.gateway;

import com.example.aiplatform.dto.InferenceRequest;
import com.example.aiplatform.dto.InferenceResponse;
import com.example.aiplatform.dto.ModelRegistrationRequest;
import com.example.aiplatform.model.ModelProfile;
import com.example.aiplatform.model.ModelProviderType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ModelGatewayService {

    private final Map<String, ModelProfile> modelRegistry = new ConcurrentHashMap<>();

    public ModelProfile register(ModelRegistrationRequest request) {
        ModelProfile profile = new ModelProfile(
                request.modelId(),
                request.displayName(),
                request.providerType(),
                request.endpoint(),
                request.apiKey(),
                request.metadata() == null ? Map.of() : request.metadata(),
                Instant.now(),
                true
        );
        modelRegistry.put(profile.modelId(), profile);
        return profile;
    }

    public List<ModelProfile> listModels() {
        return new ArrayList<>(modelRegistry.values());
    }

    public InferenceResponse routeInference(InferenceRequest request) {
        long start = System.currentTimeMillis();
        ModelProfile target = chooseModel(request);
        boolean degraded = false;
        if (target == null && request.fallbackEnabled()) {
            target = firstHealthy();
            degraded = true;
        }

        if (target == null) {
            return new InferenceResponse("unavailable", "No model available for routing", true, System.currentTimeMillis() - start);
        }

        String simulatedResponse = "[" + target.displayName() + "] 已处理请求: " + request.prompt();
        return new InferenceResponse(target.modelId(), simulatedResponse, degraded, System.currentTimeMillis() - start);
    }

    private ModelProfile chooseModel(InferenceRequest request) {
        if (request.preferredModel() != null) {
            ModelProfile preferred = modelRegistry.get(request.preferredModel());
            if (preferred != null && preferred.healthy()) {
                return preferred;
            }
        }

        return modelRegistry.values().stream()
                .filter(ModelProfile::healthy)
                .sorted((left, right) -> {
                    int leftWeight = providerWeight(left.providerType());
                    int rightWeight = providerWeight(right.providerType());
                    return Integer.compare(rightWeight, leftWeight);
                })
                .findFirst()
                .orElse(null);
    }

    private ModelProfile firstHealthy() {
        return modelRegistry.values().stream().filter(ModelProfile::healthy).findFirst().orElse(null);
    }

    private int providerWeight(ModelProviderType type) {
        return switch (type) {
            case SELF_HOSTED -> 3;
            case DOMESTIC_VENDOR -> 2;
            case OPENAI_COMPATIBLE -> 1;
        };
    }
}
