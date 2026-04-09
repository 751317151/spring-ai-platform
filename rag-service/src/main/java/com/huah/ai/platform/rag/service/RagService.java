package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.rag.model.RetrievedChunk;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * RAG 妫€绱㈠寮虹敓鎴愭湇鍔?
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient.Builder chatClientBuilder;

    private static final String RAG_SYSTEM_PROMPT = """
            浣犳槸浼佷笟鐭ヨ瘑搴撴櫤鑳藉姪鎵嬨€傝涓ユ牸鍩轰簬鐢ㄦ埛娑堟伅涓彁渚涚殑涓婁笅鏂囪祫鏂欏洖绛旈棶棰樸€?
            瑙勫垯锛?            1. 鍙娇鐢ㄤ笂涓嬫枃涓殑淇℃伅鍥炵瓟锛屼笉瑕佸嚟绌烘崗閫?            2. 濡傛灉涓婁笅鏂囦腑娌℃湁鐩稿叧淇℃伅锛岃鏄庣‘璇存槑"鐭ヨ瘑搴撲腑鏈壘鍒扮浉鍏充俊鎭?
            3. 鍥炵瓟瑕佺畝娲併€佸噯纭€佷笓涓?            4. 濡傛灉寮曠敤浜嗗叿浣撹祫鏂欙紝璇锋爣娉ㄦ潵婧愭枃浠跺悕
            """;

    public String answer(String question, List<Map<String, String>> history, List<RetrievedChunk> sources) {
        return buildChatClient()
                .prompt()
                .system(RAG_SYSTEM_PROMPT)
                .user(buildUserPrompt(question, history, sources))
                .call()
                .content();
    }

    public Flux<String> answerStream(String question, List<Map<String, String>> history, List<RetrievedChunk> sources) {
        return buildChatClient()
                .prompt()
                .system(RAG_SYSTEM_PROMPT)
                .user(buildUserPrompt(question, history, sources))
                .stream()
                .content();
    }

    public String queryWithHistory(
            String question, Long knowledgeBaseId, List<Map<String, String>> history, int topK) {
        return answer(question, history, searchByVector(question, knowledgeBaseId, topK));
    }

    /**
     * 绾悜閲忔绱紙涓嶇敓鎴愶紝浠呰繑鍥炵浉浼兼枃妗ｏ級
     */
    public List<Document> search(String query, Long knowledgeBaseId, int topK) {
        SearchRequest request = buildSearchRequest(query, knowledgeBaseId, topK);
        return vectorStore.similaritySearch(request);
    }

    public List<RetrievedChunk> searchByVector(String query, Long knowledgeBaseId, int topK) {
        return search(query, knowledgeBaseId, topK).stream()
                .map(this::mapDocument)
                .toList();
    }

    private SearchRequest buildSearchRequest(String query, Long knowledgeBaseId, int topK) {
        var builder = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.2);

        if (knowledgeBaseId != null) {
            FilterExpressionBuilder fb = new FilterExpressionBuilder();
            builder.filterExpression(fb.eq("kb_id", String.valueOf(knowledgeBaseId)).build());
        }

        return builder.build();
    }

    private ChatClient buildChatClient() {
        return chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    private RetrievedChunk mapDocument(Document document) {
        Object distanceObj = document.getMetadata().getOrDefault("distance", 0.0);
        double distance = distanceObj instanceof Number number ? number.doubleValue() : 0.0d;
        return RetrievedChunk.builder()
                .documentId(asLong(document.getMetadata().get("doc_id")))
                .chunkId(asString(document.getId()))
                .chunkIndex(asInteger(document.getMetadata().get("chunk_index")))
                .filename(asString(document.getMetadata().getOrDefault("filename", "unknown")))
                .preview(asString(document.getMetadata().get("chunk_preview")))
                .content(document.getText() == null ? "" : document.getText())
                .semanticScore(normalizeVectorDistance(distance))
                .build();
    }

    private String buildUserPrompt(String question, List<Map<String, String>> history, List<RetrievedChunk> sources) {
        String historyBlock = formatHistory(history);
        String evidenceBlock = formatEvidence(sources);
        return """
                用户问题：
                %s

                对话历史：
                %s

                可用证据：
                %s

                回答要求：
                1. 仅基于“可用证据”回答。
                2. 如果证据不足，请明确说明知识库中未找到足够证据。
                3. 尽量引用文件名，必要时带上分段编号。
                4. 先给结论，再给关键依据。
                """.formatted(question, historyBlock, evidenceBlock);
    }

    private String formatHistory(List<Map<String, String>> history) {
        if (history == null || history.isEmpty()) {
            return "无";
        }
        return history.stream()
                .filter(Objects::nonNull)
                .map(item -> item.getOrDefault("role", "unknown") + ": " + item.getOrDefault("content", ""))
                .collect(Collectors.joining("\n"));
    }

    private String formatEvidence(List<RetrievedChunk> sources) {
        if (sources == null || sources.isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            RetrievedChunk source = sources.get(i);
            builder.append("[")
                    .append(i + 1)
                    .append("] 文件=")
                    .append(source.getFilename() == null ? "unknown" : source.getFilename());
            if (source.getChunkIndex() != null) {
                builder.append(", 分段=").append(source.getChunkIndex());
            }
            builder.append(", 分数=")
                    .append(String.format("%.3f", source.getRerankScore() > 0 ? source.getRerankScore() : source.getSemanticScore()))
                    .append("\n")
                    .append(source.getContent() == null ? "" : source.getContent())
                    .append("\n\n");
        }
        return builder.toString().trim();
    }

    private double normalizeVectorDistance(double distance) {
        if (Double.isNaN(distance) || Double.isInfinite(distance)) {
            return 0.0d;
        }
        if (distance <= 0) {
            return 1.0d;
        }
        return 1.0d / (1.0d + distance);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : Long.parseLong(text);
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return null;
    }
}
