package com.huah.ai.platform.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 服务 ChatClient 配置
 * RAG 检索 advisor 由 RagService 在每次查询时动态注入（带 kb_id 过滤）
 */
@Configuration
public class RagChatConfig {

    @Bean
    public ChatClient.Builder chatClientBuilder(@Qualifier("dashScopeChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    private static final String RAG_SYSTEM_PROMPT = """
            你是企业知识库智能助手。请严格基于用户消息中提供的上下文资料回答问题。

            规则：
            1. 只使用上下文中的信息回答，不要凭空捏造
            2. 如果上下文中没有相关信息，请明确说明"知识库中未找到相关信息"
            3. 回答要简洁、准确、专业
            4. 如果引用了具体资料，请标注来源文件名
            """;

    @Bean
    public ChatClient chatClient(@Qualifier("dashScopeChatModel") ChatModel model) {
        return ChatClient
                .builder(model)
                .defaultSystem(RAG_SYSTEM_PROMPT)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
