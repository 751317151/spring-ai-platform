package com.huah.ai.platform.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 服务 ChatClient 配置
 * Spring AI 1.0 需要显式声明 ChatClient.Builder Bean
 */
@Configuration
public class RagChatConfig {

    /**
     * 提供 ChatClient.Builder，供 RagService 注入使用
     * chatModel 由 spring-ai-openai-spring-boot-starter 自动配置注入
     */
    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }
}
