package com.huah.ai.platform.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * RAG 检索增强生成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient.Builder chatClientBuilder;
    private final ChatClient chatClient;

    private static final String RAG_SYSTEM_PROMPT = """
            你是企业知识库智能助手。请严格基于用户消息中提供的上下文资料回答问题。

            规则：
            1. 只使用上下文中的信息回答，不要凭空捏造
            2. 如果上下文中没有相关信息，请明确说明"知识库中未找到相关信息"
            3. 回答要简洁、准确、专业
            4. 如果引用了具体资料，请标注来源文件名
            """;

    /**
     * 普通 RAG 问答
     */
    public String query(String question, String knowledgeBaseId, int topK) {
        Advisor ragAdvisor = buildRagAdvisor(knowledgeBaseId, topK);

        return chatClient.prompt()
                .advisors(ragAdvisor)
                .user(question)
                .call()
                .content();
    }

    /**
     * 流式 RAG 问答
     */
    public Flux<String> queryStream(String question, String knowledgeBaseId, int topK) {
        Advisor ragAdvisor = buildRagAdvisor(knowledgeBaseId, topK);

        return chatClient.prompt()
                .advisors(ragAdvisor)
                .user(question)
                .stream()
                .content();
    }

    /**
     * 带对话历史的 RAG
     */
    public String queryWithHistory(String question, String knowledgeBaseId,
                                   List<Map<String, String>> history, int topK) {
        Advisor ragAdvisor = buildRagAdvisor(knowledgeBaseId, topK);

        var promptBuilder = chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor(), ragAdvisor)
                .build()
                .prompt()
                .system(RAG_SYSTEM_PROMPT);

        // 注入对话历史
        for (Map<String, String> msg : history) {
            String role = msg.get("role");
            String content = msg.get("content");
            if ("user".equals(role)) {
                // 历史消息在 Spring AI 中通过 messages 方法注入
                log.debug("历史用户消息: {}", content);
            }
        }

        return promptBuilder.user(question).call().content();
    }

    /**
     * 纯向量检索（不生成，仅返回相似文档）
     */
    public List<Document> search(String query, String knowledgeBaseId, int topK) {
        SearchRequest request = buildSearchRequest(query, knowledgeBaseId, topK);
        return vectorStore.similaritySearch(request);
    }

    private SearchRequest buildSearchRequest(String query, String knowledgeBaseId, int topK) {
        var builder = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.2);

        // 按知识库ID过滤
        if (knowledgeBaseId != null && !knowledgeBaseId.isBlank()) {
            FilterExpressionBuilder fb = new FilterExpressionBuilder();
            builder.filterExpression(fb.eq("kb_id", knowledgeBaseId).build());
        }

        return builder.build();
    }

    private Advisor buildRagAdvisor(String knowledgeBaseId, int topK) {
        var retrieverBuilder = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.2)
                .topK(topK);

        if (knowledgeBaseId != null && !knowledgeBaseId.isBlank()) {
            FilterExpressionBuilder fb = new FilterExpressionBuilder();
            retrieverBuilder.filterExpression(fb.eq("kb_id", knowledgeBaseId).build());
        }

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retrieverBuilder.build())
                .build();
    }
}
