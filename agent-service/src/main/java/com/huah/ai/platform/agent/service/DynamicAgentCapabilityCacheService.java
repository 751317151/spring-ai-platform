package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.config.CacheConfiguration;
import com.huah.ai.platform.agent.entity.AgentDefinitionEntity;
import java.util.List;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class DynamicAgentCapabilityCacheService {

    private final AgentDefinitionService agentDefinitionService;
    private final AssistantCapabilityResolverService assistantCapabilityResolverService;

    public DynamicAgentCapabilityCacheService(AgentDefinitionService agentDefinitionService,
                                              AssistantCapabilityResolverService assistantCapabilityResolverService) {
        this.agentDefinitionService = agentDefinitionService;
        this.assistantCapabilityResolverService = assistantCapabilityResolverService;
    }

    @Cacheable(cacheNames = CacheConfiguration.CACHE_DYNAMIC_AGENT_CAPABILITIES, key = "#agentCode")
    public DynamicAgentCapabilities getCapabilities(String agentCode) {
        AgentDefinitionEntity definition = agentDefinitionService.getRequiredEnabledEntity(agentCode);
        List<Object> tools = List.copyOf(List.of(assistantCapabilityResolverService.resolveToolBeans(definition)));
        ToolCallbackProvider toolCallbackProvider =
                assistantCapabilityResolverService.resolveMcpToolCallbackProvider(definition);
        return new DynamicAgentCapabilities(tools, toolCallbackProvider);
    }

    @CacheEvict(cacheNames = CacheConfiguration.CACHE_DYNAMIC_AGENT_CAPABILITIES, key = "#agentCode")
    public void evict(String agentCode) {
    }

    public record DynamicAgentCapabilities(List<Object> tools, ToolCallbackProvider toolCallbackProvider) {

        public Object[] toolArray() {
            return tools.toArray();
        }
    }
}
