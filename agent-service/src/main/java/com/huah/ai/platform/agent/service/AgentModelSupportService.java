package com.huah.ai.platform.agent.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai.models")
public class AgentModelSupportService {

    public static final String AUTO_MODEL = "auto";

    private List<ModelDefinition> registry = List.of();

    public String normalizeDefaultModel(String modelId) {
        String normalized = trimToNull(modelId);
        if (normalized == null) {
            return AUTO_MODEL;
        }
        if (AUTO_MODEL.equalsIgnoreCase(normalized)) {
            return AUTO_MODEL;
        }
        if (normalized.regionMatches(true, 0, AUTO_MODEL, 0, AUTO_MODEL.length())) {
            throw new IllegalArgumentException("defaultModel is unsupported: " + normalized);
        }
        Set<String> configuredModelIds = getConfiguredModelIds();
        if (!configuredModelIds.isEmpty() && !configuredModelIds.contains(normalized)) {
            throw new IllegalArgumentException("defaultModel is unsupported: " + normalized);
        }
        return normalized;
    }

    public boolean shouldApplyExplicitModel(String modelId) {
        String normalized = trimToNull(modelId);
        if (normalized == null || AUTO_MODEL.equalsIgnoreCase(normalized)) {
            return false;
        }
        if (normalized.regionMatches(true, 0, AUTO_MODEL, 0, AUTO_MODEL.length())) {
            return false;
        }
        Set<String> configuredModelIds = getConfiguredModelIds();
        return configuredModelIds.isEmpty() || configuredModelIds.contains(normalized);
    }

    public void setRegistry(List<ModelDefinition> registry) {
        this.registry = registry == null ? List.of() : List.copyOf(registry);
    }

    public List<ModelDefinition> getRegistry() {
        return registry;
    }

    private Set<String> getConfiguredModelIds() {
        Set<String> modelIds = new LinkedHashSet<>();
        for (ModelDefinition modelDefinition : registry) {
            if (modelDefinition == null) {
                continue;
            }
            String id = trimToNull(modelDefinition.getId());
            if (id != null) {
                modelIds.add(id);
            }
        }
        return modelIds;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Data
    public static class ModelDefinition {
        private String id;
    }
}
