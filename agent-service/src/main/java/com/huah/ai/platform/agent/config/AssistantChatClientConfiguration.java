package com.huah.ai.platform.agent.config;

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
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AssistantChatClientConfiguration {

    private final AgentChatClientFactory chatClientFactory;
    private final RdTools rdTools;
    private final SalesTools salesTools;
    private final HrTools hrTools;
    private final FinanceTools financeTools;
    private final SupplyChainTools supplyChainTools;
    private final QcTools qcTools;
    private final WeatherTools weatherTools;
    private final SearchTools searchTools;
    private final DataAnalysisTools dataAnalysisTools;
    private final CodeTools codeTools;
    private final InternalApiTools internalApiTools;

    @Bean
    public ChatClient rdChatClient(ChatModel model, ChatMemory chatMemory) {
        return chatClientFactory.buildChatClient(model, chatMemory, AgentSystemPrompts.RD, rdTools, internalApiTools);
    }

    @Bean
    public ChatClient salesChatClient(ChatModel model, ChatMemory chatMemory) {
        return chatClientFactory.buildChatClient(model, chatMemory, AgentSystemPrompts.SALES, salesTools);
    }

    @Bean
    public ChatClient supplyChainChatClient(ChatModel model, ChatMemory chatMemory) {
        return chatClientFactory.buildChatClient(model, chatMemory, AgentSystemPrompts.SUPPLY_CHAIN, supplyChainTools);
    }

    @Bean
    public ChatClient financeChatClient(ChatModel model, ChatMemory chatMemory) {
        return chatClientFactory.buildChatClient(model, chatMemory, AgentSystemPrompts.FINANCE, financeTools);
    }

    @Bean
    public ChatClient qcChatClient(ChatModel model, ChatMemory chatMemory) {
        return chatClientFactory.buildChatClient(model, chatMemory, AgentSystemPrompts.QC, qcTools);
    }

    @Bean
    public ChatClient hrChatClient(ChatModel model, ChatMemory chatMemory) {
        return chatClientFactory.buildChatClient(model, chatMemory, AgentSystemPrompts.HR, hrTools);
    }

    @Bean
    public ChatClient weatherChatClient(ChatModel model, ChatMemory chatMemory) {
        return chatClientFactory.buildChatClient(model, chatMemory, AgentSystemPrompts.WEATHER, weatherTools);
    }

    @Bean
    public ChatClient searchChatClient(ChatModel model, ChatMemory chatMemory) {
        return chatClientFactory.buildChatClient(model, chatMemory, AgentSystemPrompts.SEARCH, searchTools);
    }

    @Bean
    public ChatClient dataAnalysisChatClient(ChatModel model, ChatMemory chatMemory) {
        return chatClientFactory.buildChatClient(model, chatMemory, AgentSystemPrompts.DATA_ANALYSIS, dataAnalysisTools);
    }

    @Bean
    public ChatClient codeChatClient(ChatModel model, ChatMemory chatMemory) {
        return chatClientFactory.buildChatClient(model, chatMemory, AgentSystemPrompts.CODE, codeTools);
    }
}
