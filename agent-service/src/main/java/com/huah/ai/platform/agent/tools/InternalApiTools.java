package com.huah.ai.platform.agent.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huah.ai.platform.agent.config.ToolsProperties;
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

    private final RestClient.Builder restClientBuilder;
    private final ToolsProperties.InternalApiConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InternalApiTools(RestClient.Builder restClientBuilder, ToolsProperties props) {
        this.restClientBuilder = restClientBuilder;
        this.config = props.getInternalApi();
    }

    @Tool(description = "列出当前已配置的内部 API connector，返回可用 connector 编码、名称和允许调用的路径前缀。")
    public List<Map<String, Object>> listConnectors() {
        return config.getConnectors().entrySet().stream()
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

    @Tool(description = "调用已配置的内部 API connector，只支持 GET 请求。必须提供 connector 编码、路径和可选查询参数 JSON。")
    public Map<String, Object> callConnector(
            @ToolParam(description = "connector 编码，例如 jira-internal、docs-api") String connectorCode,
            @ToolParam(description = "GET 路径，例如 /api/issues 或 /v1/docs/search") String path,
            @ToolParam(description = "可选查询参数 JSON，例如 {\"status\":\"OPEN\",\"limit\":10}，为空时传 {}") String queryParamsJson) {
        try {
            ToolsProperties.ConnectorDefinition connector = requireConnector(connectorCode);
            String normalizedPath = normalizePath(path);
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
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        } catch (Exception e) {
            log.error("[Tool] callConnector failed: connector={}, path={}, error={}", connectorCode, path, e.getMessage(), e);
            return Map.of("error", "内部 API 调用失败: " + e.getMessage());
        }
    }

    private ToolsProperties.ConnectorDefinition requireConnector(String connectorCode) {
        if (connectorCode == null || connectorCode.isBlank()) {
            throw new IllegalArgumentException("connectorCode不能为空");
        }
        ToolsProperties.ConnectorDefinition connector = config.getConnectors().get(connectorCode);
        if (connector == null) {
            throw new IllegalArgumentException("未找到内部 API connector: " + connectorCode);
        }
        if (!connector.isEnabled()) {
            throw new IllegalArgumentException("内部 API connector 未启用: " + connectorCode);
        }
        if (connector.getBaseUrl() == null || connector.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("内部 API connector 缺少 baseUrl 配置: " + connectorCode);
        }
        return connector;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path不能为空");
        }
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            throw new IllegalArgumentException("path 必须以 / 开头");
        }
        if (normalized.contains("://") || normalized.contains("..")) {
            throw new IllegalArgumentException("path 非法");
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
            throw new IllegalArgumentException("当前 path 不在 connector 允许范围内: " + path);
        }
    }

    private Map<String, Object> parseQueryParams(String queryParamsJson) {
        if (queryParamsJson == null || queryParamsJson.isBlank() || "{}".equals(queryParamsJson.trim())) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(queryParamsJson, new TypeReference<>() {});
            return parsed != null ? parsed : Map.of();
        } catch (Exception e) {
            throw new IllegalArgumentException("queryParamsJson 必须是合法 JSON 对象");
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
                return objectMapper.readValue(trimmed, new TypeReference<Map<String, Object>>() {});
            }
            if (trimmed.startsWith("[")) {
                return objectMapper.readValue(trimmed, new TypeReference<List<Object>>() {});
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
