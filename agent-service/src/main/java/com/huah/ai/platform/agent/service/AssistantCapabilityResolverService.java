package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.dto.AssistantToolCatalogItemResponse;
import com.huah.ai.platform.agent.entity.AgentDefinitionEntity;
import com.huah.ai.platform.agent.tools.CodeTools;
import com.huah.ai.platform.agent.tools.DataAnalysisTools;
import com.huah.ai.platform.agent.tools.FinanceTools;
import com.huah.ai.platform.agent.tools.HrTools;
import com.huah.ai.platform.agent.tools.InternalApiTools;
import com.huah.ai.platform.agent.tools.QcTools;
import com.huah.ai.platform.agent.tools.RdTools;
import com.huah.ai.platform.agent.tools.SalesTools;
import com.huah.ai.platform.agent.tools.SearchTools;
import com.huah.ai.platform.agent.tools.SupplyChainTools;
import com.huah.ai.platform.agent.tools.WeatherTools;
import io.modelcontextprotocol.client.McpSyncClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

@Service
public class AssistantCapabilityResolverService {

    private final Map<String, ToolBinding> toolBindings;
    private final Map<String, McpSyncClient> mcpClientsByServerCode;
    private final McpServerCatalogService mcpServerCatalogService;

    public AssistantCapabilityResolverService(
            RdTools rdTools,
            SalesTools salesTools,
            HrTools hrTools,
            FinanceTools financeTools,
            SupplyChainTools supplyChainTools,
            QcTools qcTools,
            WeatherTools weatherTools,
            SearchTools searchTools,
            DataAnalysisTools dataAnalysisTools,
            CodeTools codeTools,
            InternalApiTools internalApiTools,
            McpServerCatalogService mcpServerCatalogService,
            org.springframework.beans.factory.ObjectProvider<Map<String, McpSyncClient>> mcpClientProvider) {
        Map<String, ToolBinding> bindings = new LinkedHashMap<>();
        register(bindings, "rd-tools", "研发工具", "研发问答、研发检索和研发系统接口", "domain", rdTools);
        register(bindings, "sales-tools", "销售工具", "销售问答和销售分析能力", "domain", salesTools);
        register(bindings, "hr-tools", "HR 工具", "人事制度与流程问答能力", "domain", hrTools);
        register(bindings, "finance-tools", "财务工具", "财务分析和报表解读能力", "domain", financeTools);
        register(bindings, "supply-chain-tools", "供应链工具", "供应链协同和状态跟踪能力", "domain", supplyChainTools);
        register(bindings, "qc-tools", "质控工具", "质量事件分析和预警能力", "domain", qcTools);
        register(bindings, "weather-tools", "天气工具", "天气查询和出行建议能力", "utility", weatherTools);
        register(bindings, "search-tools", "搜索工具", "联网搜索和网页摘要能力", "utility", searchTools);
        register(bindings, "data-analysis-tools", "数据分析工具", "数据库查询和数据分析能力", "data", dataAnalysisTools);
        register(bindings, "code-tools", "代码工具", "代码分析和实现建议能力", "engineering", codeTools);
        register(bindings, "internal-api", "内部接口工具", "企业内部连接器和内部接口访问能力", "integration", internalApiTools);
        this.toolBindings = Map.copyOf(bindings);
        this.mcpServerCatalogService = mcpServerCatalogService;
        this.mcpClientsByServerCode = normalizeMcpClients(mcpClientProvider.getIfAvailable(Map::of));
    }

    public List<AssistantToolCatalogItemResponse> listToolCatalog() {
        return toolBindings.values().stream()
                .map(binding -> AssistantToolCatalogItemResponse.builder()
                        .code(binding.code())
                        .name(binding.name())
                        .description(binding.description())
                        .category(binding.category())
                        .build())
                .toList();
    }

    public Set<String> supportedToolCodes() {
        return toolBindings.keySet();
    }

    public Set<String> supportedMcpServerCodes() {
        return mcpServerCatalogService.listServers().getServers().stream()
                .map(item -> normalizeCode(item.getCode()))
                .filter(item -> !item.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public Object[] resolveToolBeans(AgentDefinitionEntity definition) {
        List<Object> tools = new ArrayList<>();
        for (String code : parseCodes(definition != null ? definition.getToolCodes() : null)) {
            ToolBinding binding = toolBindings.get(code);
            if (binding != null) {
                tools.add(binding.toolBean());
            }
        }
        return tools.toArray();
    }

    public ToolCallbackProvider resolveMcpToolCallbackProvider(AgentDefinitionEntity definition) {
        List<McpSyncClient> selectedClients = new ArrayList<>();
        for (String code : parseCodes(definition != null ? definition.getMcpServerCodes() : null)) {
            McpSyncClient client = mcpClientsByServerCode.get(code);
            if (client != null) {
                selectedClients.add(client);
            }
        }
        if (selectedClients.isEmpty()) {
            return null;
        }
        return new SyncMcpToolCallbackProvider(selectedClients);
    }

    public String normalizeSelectedToolCodes(String codes) {
        return normalizeCodes(codes, supportedToolCodes());
    }

    public String normalizeSelectedMcpServerCodes(String codes) {
        return normalizeCodes(codes, supportedMcpServerCodes());
    }

    private Map<String, McpSyncClient> normalizeMcpClients(Map<String, McpSyncClient> rawClients) {
        Map<String, McpSyncClient> clients = new LinkedHashMap<>();
        if (rawClients == null) {
            return clients;
        }
        for (Map.Entry<String, McpSyncClient> entry : rawClients.entrySet()) {
            String normalizedKey = normalizeMcpClientBeanName(entry.getKey());
            if (!normalizedKey.isBlank()) {
                clients.putIfAbsent(normalizedKey, entry.getValue());
            }
            String serverName = normalizeCode(resolveServerName(entry.getValue()));
            if (!serverName.isBlank()) {
                clients.putIfAbsent(serverName, entry.getValue());
            }
        }
        return clients;
    }

    private String resolveServerName(McpSyncClient client) {
        try {
            if (client == null || client.getServerInfo() == null || client.getServerInfo().name() == null) {
                return "";
            }
            return client.getServerInfo().name();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String normalizeMcpClientBeanName(String beanName) {
        String normalized = normalizeCode(beanName);
        if (normalized.endsWith("mcpsyncclient")) {
            normalized = normalized.substring(0, normalized.length() - "mcpsyncclient".length());
        } else if (normalized.endsWith("syncmcpclient")) {
            normalized = normalized.substring(0, normalized.length() - "syncmcpclient".length());
        } else if (normalized.endsWith("mcpclient")) {
            normalized = normalized.substring(0, normalized.length() - "mcpclient".length());
        }
        return normalized;
    }

    private String normalizeCodes(String codes, Collection<String> supportedCodes) {
        Set<String> normalized = new LinkedHashSet<>();
        Set<String> supported = supportedCodes.stream()
                .map(this::normalizeCode)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (String code : parseCodes(codes)) {
            if (!supported.contains(code)) {
                throw new IllegalArgumentException("Unsupported capability code: " + code);
            }
            normalized.add(code);
        }
        return String.join(",", normalized);
    }

    private List<String> parseCodes(String codes) {
        if (codes == null || codes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(codes.split(","))
                .map(this::normalizeCode)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private String normalizeCode(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void register(Map<String, ToolBinding> bindings,
                          String code,
                          String name,
                          String description,
                          String category,
                          Object toolBean) {
        bindings.put(code, new ToolBinding(code, name, description, category, toolBean));
    }

    private record ToolBinding(
            String code,
            String name,
            String description,
            String category,
            Object toolBean) {
    }
}
