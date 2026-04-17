package com.huah.ai.platform.agent.config;

import com.huah.ai.platform.agent.advisor.ToolExecutionStreamAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class AgentChatClientFactory {

    public ChatClient buildChatClient(ChatModel model, ChatMemory chatMemory, Object... tools) {
        return ChatClient
                .builder(model)
                .defaultTools(tools)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    public ChatClient buildChatClient(ChatModel model, ChatMemory chatMemory, String systemPrompt, Object... tools) {
        return ChatClient
                .builder(model)
                .defaultSystem(systemPrompt)
                .defaultTools(tools)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    public ChatClient buildChatClientWithToolStatus(ChatModel model,
                                                     ChatMemory chatMemory,
                                                     String systemPrompt,
                                                     SseEmitter emitter,
                                                     Object... tools) {
        ChatClient.Builder builder = ChatClient.builder(model);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.defaultSystem(systemPrompt);
        }
        builder.defaultAdvisors(
                new SimpleLoggerAdvisor(),
                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                new ToolExecutionStreamAdvisor(emitter)
        );
        if (tools != null && tools.length > 0) {
            builder.defaultTools(tools);
        }
        return builder.build();
    }

    public ChatClient buildDynamicChatClient(ChatModel model,
                                             ChatMemory chatMemory,
                                             String systemPrompt,
                                             ToolCallbackProvider toolCallbackProvider,
                                             Object... tools) {
        ChatClient.Builder builder = ChatClient.builder(model);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.defaultSystem(systemPrompt);
        }
        builder.defaultAdvisors(
                new SimpleLoggerAdvisor(),
                MessageChatMemoryAdvisor.builder(chatMemory).build()
        );
        if (tools != null && tools.length > 0) {
            builder.defaultTools(tools);
        }
        if (toolCallbackProvider != null) {
            builder.defaultToolCallbacks(toolCallbackProvider);
        }
        return builder.build();
    }

    public ChatClient buildMcpChatClient(ChatModel model,
                                         ChatMemory chatMemory,
                                         ToolCallbackProvider toolCallbackProvider,
                                         String systemPrompt) {
        return ChatClient
                .builder(model)
                .defaultSystem(systemPrompt)
                .defaultToolCallbacks(toolCallbackProvider)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }
}
