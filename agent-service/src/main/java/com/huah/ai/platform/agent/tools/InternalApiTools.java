package com.huah.ai.platform.agent.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huah.ai.platform.agent.audit.ToolExecutionContext;
import com.huah.ai.platform.agent.config.ToolsProperties;
import com.huah.ai.platform.agent.security.ToolAccessDeniedException;
import com.huah.ai.platform.agent.security.ToolSecurityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class InternalApiTools {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {
    };

    private final RestClient.Builder restClientBuilder;
    private final ToolsProperties.InternalApiConfig config;
    private final ToolSecurityService toolSecurityService;
    private final ObjectMapper objectMapper;

    public InternalApiTools(RestClient.Builder restClientBuilder,
                            ToolsProperties props,
                            ToolSecurityService toolSecurityService,
                            ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.config = props.getInternalApi();
        this.toolSecurityService = toolSecurityService;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "List configured internal API connectors that are visible to the current agent.")
    public List<Map<String, Object>> listConnectors() {
        String agentType = currentAgentType();
        List<String> allowedConnectorCodes = toolSecurityService.filterAuthorizedConnectors(
                agentType,
                config.getConnectors().keySet());

        return allowedConnectorCodes.stream()
                .map(code -> Map.entry(code, config.getConnectors().get(code)))
                .filter(entry -> entry.getValue() != null && entry.getValue().isEnabled())
                .map(entry -> {
                    ToolsProperties.ConnectorDefinition connector = entry.getValue();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("code", entry.getKey());
                    item.put("name", connector.getName() != null ? connector.getName() : entry.getKey());
                    item.put("baseUrl", connector.getBaseUrl());
                    item.put("allowedPathPrefixes", connector.getAllowedPathPrefixes());
                    return item;
                })
                .toList();
    }

    @Tool(description = "Call an allowed internal API connector with a GET request and optional JSON query parameters.")
    public Map<String, Object> callConnector(
            @ToolParam(description = "Connector code, for example jira-internal or docs-api") String connectorCode,
            @ToolParam(description = "GET path, for example /api/issues or /v1/docs/search") String path,
            @ToolParam(description = "Optional query parameters in JSON, for example {\"status\":\"OPEN\",\"limit\":10}") String queryParamsJson) {
        try {
            toolSecurityService.validateConnectorAccess(connectorCode, ToolExecutionContext.current());
            ToolsProperties.ConnectorDefinition connector = requireConnector(connectorCode);
            String normalizedPath = normalizePath(path);
            toolSecurityService.validateConnectorPathAccess(
                    connectorCode,
                    normalizedPath,
                    ToolExecutionContext.current(),
                    connector.getAllowedPathPrefixes()
            );
            validatePathAllowed(connector, normalizedPath);
            Map<String, Object> queryParams = parseQueryParams(queryParamsJson);

            String url = buildUrl(connector.getBaseUrl(), normalizedPath, queryParams);
            RestClient client = restClientBuilder.clone()
                    .baseUrl(connector.getBaseUrl())
                    .build();

            log.info("[Tool] callConnector: connector={}, path={}, query={}", connectorCode, normalizedPath, queryParams);

            RestClient.RequestHeadersSpec<?> spec = client.get().uri(url);
            if (connector.getAuthHeaderName() != null && !connector.getAuthHeaderName().isBlank()
                    && connector.getAuthHeaderValue() != null && !connector.getAuthHeaderValue().isBlank()) {
                spec = spec.header(connector.getAuthHeaderName(), connector.getAuthHeaderValue());
            }

            String responseText = spec.accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("connector", connectorCode);
            result.put("path", normalizedPath);
            result.put("url", url);
            result.put("query", queryParams);
            result.put("response", parseResponseBody(responseText));
            return result;
        } catch (ToolAccessDeniedException e) {
            return ToolResponseSupport.accessDenied(e);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ToolResponseSupport.error(e.getMessage(), "INVALID_ARGUMENT");
        } catch (Exception e) {
            log.error("[Tool] callConnector failed: connector={}, path={}, error={}", connectorCode, path, e.getMessage(), e);
            return ToolResponseSupport.error("Internal API call failed: " + e.getMessage(), "INTERNAL_API_CALL_FAILED");
        }
    }

    public List<String> listEnabledConnectorCodes() {
        return config.getConnectors().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().isEnabled())
                .map(Map.Entry::getKey)
                .toList();
    }

    private String currentAgentType() {
        ToolExecutionContext.Context context = ToolExecutionContext.current();
        return context != null ? context.getAgentType() : null;
    }

    private ToolsProperties.ConnectorDefinition requireConnector(String connectorCode) {
        if (connectorCode == null || connectorCode.isBlank()) {
            throw new IllegalArgumentException("connectorCode must not be blank");
        }
        ToolsProperties.ConnectorDefinition connector = config.getConnectors().get(connectorCode);
        if (connector == null) {
            throw new IllegalArgumentException("Unknown internal API connector: " + connectorCode);
        }
        if (!connector.isEnabled()) {
            throw new IllegalArgumentException("The internal API connector is disabled: " + connectorCode);
        }
        if (connector.getBaseUrl() == null || connector.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("The internal API connector is missing baseUrl: " + connectorCode);
        }
        return connector;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            throw new IllegalArgumentException("path must start with '/'");
        }
        if (normalized.contains("://") || normalized.contains("..")) {
            throw new IllegalArgumentException("path contains an invalid traversal or absolute URL");
        }
        return normalized;
    }

    private void validatePathAllowed(ToolsProperties.ConnectorDefinition connector, String path) {
        List<String> allowedPrefixes = connector.getAllowedPathPrefixes();
        if (allowedPrefixes == null || allowedPrefixes.isEmpty()) {
            return;
        }
        boolean matched = allowedPrefixes.stream().anyMatch(path::startsWith);
        if (!matched) {
            throw new IllegalArgumentException("The requested path is outside the connector allowlist: " + path);
        }
    }

    private Map<String, Object> parseQueryParams(String queryParamsJson) {
        if (queryParamsJson == null || queryParamsJson.isBlank() || "{}".equals(queryParamsJson.trim())) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(queryParamsJson, MAP_TYPE);
            return parsed != null ? parsed : Map.of();
        } catch (Exception e) {
            throw new IllegalArgumentException("queryParamsJson must be valid JSON");
        }
    }

    private String buildUrl(String baseUrl, String path, Map<String, Object> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(trimTrailingSlash(baseUrl) + path);
        queryParams.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                builder.queryParam(key, value);
            }
        });
        return builder.build(true).toUriString();
    }

    private Object parseResponseBody(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return "";
        }
        String trimmed = responseText.trim();
        try {
            if (trimmed.startsWith("{")) {
                return objectMapper.readValue(trimmed, MAP_TYPE);
            }
            if (trimmed.startsWith("[")) {
                return objectMapper.readValue(trimmed, LIST_TYPE);
            }
        } catch (Exception e) {
            log.debug("connector response is not valid json: {}", e.getMessage());
        }
        return responseText;
    }

    private String trimTrailingSlash(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
