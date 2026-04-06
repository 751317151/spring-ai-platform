package com.huah.ai.platform.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huah.ai.platform.agent.dto.McpServerInfo;
import com.huah.ai.platform.agent.dto.McpServerListResponse;
import com.huah.ai.platform.agent.security.ToolAccessDecision;
import com.huah.ai.platform.agent.security.ToolSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
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
    private static final String MCP_SERVERS_SOURCE = "classpath:mcp-servers.json";
    private static final String STATUS_READY = "ready";
    private static final String STATUS_ISSUE = "issue";
    private static final String STATUS_DISABLED = "disabled";
    private static final String ISSUE_CLIENT_AND_SERVER_DISABLED = "client disabled, server disabled";
    private static final String ISSUE_SERVER_DISABLED = "server disabled";
    private static final String ISSUE_CLIENT_DISABLED = "client disabled";
    private static final String ISSUE_MISSING_COMMAND = "missing command";
    private static final String ISSUE_COMMAND_NOT_FOUND = "command not found";
    private static final String ISSUE_ENTRY_FILE_NOT_FOUND = "entry file not found";

    private final ObjectMapper objectMapper;
    private final ToolSecurityService toolSecurityService;

    @Value("${spring.ai.mcp.client.enabled:false}")
    private boolean clientEnabled;

    @Value("classpath:mcp-servers.json")
    private Resource mcpServersResource;

    public McpServerListResponse listServers() {
        return buildResponse(null, false);
    }

    public McpServerListResponse listServers(String agentType) {
        return buildResponse(agentType, true);
    }

    public List<McpServerInfo> listAllServers(String agentType) {
        return readServers().stream()
                .map(item -> withAuthorization(item, agentType))
                .toList();
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
                    .map(entry -> toInfo(entry.getKey(), entry.getValue(), null))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read MCP server configuration", e);
        }
    }

    private McpServerListResponse buildResponse(String agentType, boolean authorizedOnly) {
        List<McpServerInfo> servers = listAllServers(agentType);

        List<McpServerInfo> visibleServers = authorizedOnly && agentType != null && !agentType.isBlank()
                ? servers.stream().filter(McpServerInfo::isAuthorized).toList()
                : servers;

        return McpServerListResponse.builder()
                .clientEnabled(clientEnabled)
                .source(MCP_SERVERS_SOURCE)
                .agentType(agentType)
                .count(visibleServers.size())
                .authorizedCount((int) servers.stream().filter(McpServerInfo::isAuthorized).count())
                .issueCount((int) visibleServers.stream().filter(item -> !STATUS_READY.equals(item.getDiagnosticStatus())).count())
                .servers(visibleServers)
                .build();
    }

    private McpServerInfo withAuthorization(McpServerInfo source, String agentType) {
        boolean authorized = true;
        ToolAccessDecision accessDecision = ToolAccessDecision.builder()
                .allowed(true)
                .reasonCode("MCP_ALLOWED")
                .reasonMessage("The current agent can access this MCP server")
                .resource("mcp:" + source.getCode())
                .detail("authorizedTools=" + (agentType == null || agentType.isBlank()
                        ? List.of()
                        : toolSecurityService.getAllowedMcpTools(agentType, source.getCode())))
                .build();
        if (agentType != null && !agentType.isBlank()) {
            accessDecision = toolSecurityService.decideMcpServerAccess(agentType, source.getCode());
            authorized = accessDecision.isAllowed();
        }
        return McpServerInfo.builder()
                .code(source.getCode())
                .command(source.getCommand())
                .args(source.getArgs())
                .enabled(source.isEnabled())
                .clientEnabled(source.isClientEnabled())
                .source(source.getSource())
                .entryFile(source.getEntryFile())
                .entryFileExists(source.isEntryFileExists())
                .commandAvailable(source.isCommandAvailable())
                .diagnosticStatus(source.getDiagnosticStatus())
                .issueReason(source.getIssueReason())
                .commandLinePreview(source.getCommandLinePreview())
                .runtimeHint(source.getRuntimeHint())
                .authorized(authorized)
                .authorizedAgentType(agentType)
                .authorizedTools(agentType == null || agentType.isBlank()
                        ? List.of()
                        : toolSecurityService.getAllowedMcpTools(agentType, source.getCode()))
                .accessReasonCode(accessDecision.getReasonCode())
                .accessReasonMessage(accessDecision.getReasonMessage())
                .accessDetail(accessDecision.getDetail())
                .build();
    }

    private McpServerInfo toInfo(String code, Map<String, Object> config, String agentType) {
        Object rawArgs = config.get("args");
        List<String> args = rawArgs instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : Collections.emptyList();
        String command = String.valueOf(config.getOrDefault("command", "")).trim();
        boolean enabled = Boolean.parseBoolean(String.valueOf(config.getOrDefault("enabled", "true")));
        String entryFile = resolveEntryFile(command, args);
        boolean commandAvailable = command.isBlank() || isCommandAvailable(command);
        boolean entryFileExists = entryFile.isBlank() || Files.exists(Paths.get(entryFile));
        String issueReason = resolveIssueReason(command, enabled, commandAvailable, entryFile, entryFileExists);
        String diagnosticStatus = resolveDiagnosticStatus(issueReason, enabled);

        return McpServerInfo.builder()
                .code(code)
                .command(command)
                .args(args)
                .enabled(enabled)
                .clientEnabled(clientEnabled)
                .source(MCP_SERVERS_SOURCE)
                .entryFile(entryFile)
                .entryFileExists(entryFileExists)
                .commandAvailable(commandAvailable)
                .diagnosticStatus(diagnosticStatus)
                .issueReason(issueReason)
                .commandLinePreview(buildCommandLinePreview(command, args))
                .runtimeHint(buildRuntimeHint(command, commandAvailable, entryFile, entryFileExists))
                .authorized(agentType == null || agentType.isBlank())
                .authorizedAgentType(agentType)
                .authorizedTools(List.of())
                .build();
    }

    private String resolveEntryFile(String command, List<String> args) {
        if (args.isEmpty()) {
            return "";
        }
        String firstArg = args.get(0);
        if (!looksLikeFilePath(firstArg)) {
            return "";
        }
        try {
            Path path = Paths.get(firstArg);
            return path.toAbsolutePath().normalize().toString();
        } catch (InvalidPathException e) {
            return firstArg;
        }
    }

    private boolean looksLikeFilePath(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.contains("\\")
                || value.contains("/")
                || value.endsWith(".js")
                || value.endsWith(".mjs")
                || value.endsWith(".cjs")
                || value.endsWith(".py")
                || value.endsWith(".jar");
    }

    private String resolveIssueReason(String command, boolean enabled, boolean commandAvailable, String entryFile, boolean entryFileExists) {
        if (!clientEnabled && !enabled) {
            return ISSUE_CLIENT_AND_SERVER_DISABLED;
        }
        if (!enabled) {
            return ISSUE_SERVER_DISABLED;
        }
        if (!clientEnabled) {
            return ISSUE_CLIENT_DISABLED;
        }
        if (command == null || command.isBlank()) {
            return ISSUE_MISSING_COMMAND;
        }
        if (!commandAvailable) {
            return ISSUE_COMMAND_NOT_FOUND;
        }
        if (!entryFile.isBlank() && !entryFileExists) {
            return ISSUE_ENTRY_FILE_NOT_FOUND;
        }
        return "";
    }

    private String resolveDiagnosticStatus(String issueReason, boolean enabled) {
        if (issueReason.isBlank()) {
            return STATUS_READY;
        }
        return enabled ? STATUS_ISSUE : STATUS_DISABLED;
    }

    private boolean isCommandAvailable(String command) {
        try {
            Path directPath = Paths.get(command);
            if (directPath.isAbsolute() || command.contains("\\") || command.contains("/")) {
                return Files.exists(directPath);
            }
        } catch (InvalidPathException ignored) {
            // fall through to PATH lookup
        }

        String pathValue = System.getenv("PATH");
        if (pathValue == null || pathValue.isBlank()) {
            return false;
        }

        List<String> executableNames = isWindows()
                ? Arrays.asList(command, command + ".exe", command + ".cmd", command + ".bat")
                : List.of(command);
        String[] directories = pathValue.split(isWindows() ? ";" : ":");
        for (String directory : directories) {
            if (directory == null || directory.isBlank()) {
                continue;
            }
            for (String executableName : executableNames) {
                try {
                    if (Files.exists(Paths.get(directory, executableName))) {
                        return true;
                    }
                } catch (InvalidPathException ignored) {
                    // ignore bad PATH entry and continue scanning
                }
            }
        }
        return false;
    }

    private String buildRuntimeHint(String command, boolean commandAvailable, String entryFile, boolean entryFileExists) {
        if (command == null || command.isBlank()) {
            return "Configure the MCP startup command first.";
        }
        if (!commandAvailable) {
            return "The startup command is not executable on the current machine. Check PATH or use an absolute path.";
        }
        if (!entryFile.isBlank() && !entryFileExists) {
            return "The configured entry file does not exist. Check the script path, mounted disk, or project directory.";
        }
        return "Command and entry file checks passed. Continue with MCP server logs or internal script diagnostics if startup still fails.";
    }

    private String buildCommandLinePreview(String command, List<String> args) {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(command),
                        args.stream())
                .filter(item -> item != null && !item.isBlank())
                .map(this::quoteIfNeeded)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private String quoteIfNeeded(String value) {
        return value.contains(" ") ? "\"" + value + "\"" : value;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
