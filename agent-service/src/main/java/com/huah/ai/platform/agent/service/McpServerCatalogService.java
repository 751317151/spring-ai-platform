package com.huah.ai.platform.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huah.ai.platform.agent.dto.McpServerInfo;
import com.huah.ai.platform.agent.dto.McpServerListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerCatalogService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    @Value("${spring.ai.mcp.client.enabled:false}")
    private boolean clientEnabled;

    @Value("classpath:mcp-servers.json")
    private Resource mcpServersResource;

    public McpServerListResponse listServers() {
        List<McpServerInfo> servers = readServers();
        return McpServerListResponse.builder()
                .clientEnabled(clientEnabled)
                .source("classpath:mcp-servers.json")
                .count(servers.size())
                .servers(servers)
                .build();
    }

    @SuppressWarnings("unchecked")
    List<McpServerInfo> readServers() {
        if (!mcpServersResource.exists()) {
            log.warn("MCP server configuration does not exist: {}", mcpServersResource);
            return List.of();
        }

        try (InputStream inputStream = mcpServersResource.getInputStream()) {
            Map<String, Object> root = objectMapper.readValue(inputStream, MAP_TYPE);
            Object rawServers = root.get("mcpServers");
            if (!(rawServers instanceof Map<?, ?> rawServerMap)) {
                return List.of();
            }

            Map<String, Map<String, Object>> serverMap = new TreeMap<>();
            rawServerMap.forEach((key, value) -> {
                if (key instanceof String code && value instanceof Map<?, ?> config) {
                    serverMap.put(code, (Map<String, Object>) config);
                }
            });

            return serverMap.entrySet().stream()
                    .map(entry -> toInfo(entry.getKey(), entry.getValue()))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read MCP server configuration", e);
        }
    }

    private McpServerInfo toInfo(String code, Map<String, Object> config) {
        Object rawArgs = config.get("args");
        List<String> args = rawArgs instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : Collections.emptyList();

        return McpServerInfo.builder()
                .code(code)
                .command(String.valueOf(config.getOrDefault("command", "")))
                .args(args)
                .enabled(Boolean.parseBoolean(String.valueOf(config.getOrDefault("enabled", "true"))))
                .clientEnabled(clientEnabled)
                .source("classpath:mcp-servers.json")
                .build();
    }
}
