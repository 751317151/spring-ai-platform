package com.example.aiplatform.rag;

import com.example.aiplatform.dto.RagIngestRequest;
import com.example.aiplatform.dto.RagQueryRequest;
import com.example.aiplatform.model.KnowledgeChunk;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final Map<String, KnowledgeChunk> chunks = new ConcurrentHashMap<>();

    public List<KnowledgeChunk> ingest(RagIngestRequest request) {
        List<String> splitContents = split(request.content(), 300);
        List<KnowledgeChunk> generated = new ArrayList<>();
        for (int i = 0; i < splitContents.size(); i++) {
            String chunkId = request.documentId() + "-" + (i + 1);
            KnowledgeChunk chunk = new KnowledgeChunk(
                    chunkId,
                    request.documentId(),
                    request.businessDomain(),
                    request.documentType(),
                    splitContents.get(i),
                    embedding(splitContents.get(i))
            );
            chunks.put(chunk.chunkId(), chunk);
            generated.add(chunk);
        }
        return generated;
    }

    public String answer(RagQueryRequest request) {
        int topK = request.topK() > 0 ? request.topK() : 3;
        List<Double> queryVector = embedding(request.question());
        List<KnowledgeChunk> recalled = chunks.values().stream()
                .filter(chunk -> request.businessDomain() == null || request.businessDomain().equalsIgnoreCase(chunk.businessDomain()))
                .sorted(Comparator.comparingDouble(chunk -> -cosine(queryVector, chunk.embedding())))
                .limit(topK)
                .toList();

        if (recalled.isEmpty()) {
            return "未检索到相关知识，请补充知识库内容后重试。";
        }

        String context = recalled.stream().map(KnowledgeChunk::content).collect(Collectors.joining("\n---\n"));
        return "基于知识库检索结果，建议回答如下：\n" + context + "\n\n结论：请结合企业流程进一步确认。";
    }

    private List<String> split(String content, int chunkSize) {
        List<String> results = new ArrayList<>();
        for (int i = 0; i < content.length(); i += chunkSize) {
            int end = Math.min(content.length(), i + chunkSize);
            results.add(content.substring(i, end));
        }
        return results;
    }

    private List<Double> embedding(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        List<Double> vector = new ArrayList<>();
        for (int i = 0; i < Math.min(24, bytes.length); i++) {
            vector.add((bytes[i] & 0xFF) / 255.0);
        }
        while (vector.size() < 24) {
            vector.add(0.0);
        }
        return vector;
    }

    private double cosine(List<Double> a, List<Double> b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
