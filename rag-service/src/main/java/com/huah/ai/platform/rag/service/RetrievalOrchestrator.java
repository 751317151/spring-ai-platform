package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.rag.mapper.VectorStoreSearchMapper;
import com.huah.ai.platform.rag.model.RecallTraceItem;
import com.huah.ai.platform.rag.model.RetrievalDebugInfo;
import com.huah.ai.platform.rag.model.RetrievalExecution;
import com.huah.ai.platform.rag.model.RetrievalRewriteResult;
import com.huah.ai.platform.rag.model.RetrievedChunk;
import com.huah.ai.platform.rag.model.VectorStoreChunkEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RetrievalOrchestrator {

    private final RagService ragService;
    private final QueryRewriteService queryRewriteService;
    private final RerankService rerankService;
    private final VectorStoreSearchMapper vectorStoreSearchMapper;

    public RetrievalExecution retrieve(String question, Long knowledgeBaseId, int topK) {
        RetrievalRewriteResult rewriteResult = queryRewriteService.rewrite(question);
        int candidateLimit = Math.max(topK * 4, topK + 6);
        List<RecallTraceItem> recallSteps = new ArrayList<>();
        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();

        mergeCandidates(
                merged,
                recall(
                        "vector-original",
                        rewriteResult.getOriginalQuery(),
                        ragService.searchByVector(rewriteResult.getOriginalQuery(), knowledgeBaseId, candidateLimit)),
                rewriteResult.getKeywords(),
                recallSteps);

        if (!rewriteResult.getRetrievalQuery().equals(rewriteResult.getOriginalQuery())) {
            mergeCandidates(
                    merged,
                    recall(
                            "vector-rewritten",
                            rewriteResult.getRetrievalQuery(),
                            ragService.searchByVector(rewriteResult.getRetrievalQuery(), knowledgeBaseId, candidateLimit)),
                    rewriteResult.getKeywords(),
                    recallSteps);
        }

        for (String alternateQuery : rewriteResult.getAlternateQueries()) {
            if (alternateQuery.equals(rewriteResult.getOriginalQuery())
                    || alternateQuery.equals(rewriteResult.getRetrievalQuery())) {
                continue;
            }
            mergeCandidates(
                    merged,
                    recall(
                            "vector-alternate",
                            alternateQuery,
                            ragService.searchByVector(alternateQuery, knowledgeBaseId, candidateLimit)),
                    rewriteResult.getKeywords(),
                    recallSteps);
        }

        if (!rewriteResult.getKeywords().isEmpty()) {
            List<RetrievedChunk> keywordChunks = vectorStoreSearchMapper
                    .searchByKeywords(knowledgeBaseId, rewriteResult.getKeywords(), candidateLimit)
                    .stream()
                    .map(this::mapKeywordChunk)
                    .toList();
            mergeCandidates(
                    merged,
                    recall("keyword", String.join(" ", rewriteResult.getKeywords()), keywordChunks),
                    rewriteResult.getKeywords(),
                    recallSteps);
        }

        List<RetrievedChunk> reranked = rerankService.rerank(new ArrayList<>(merged.values()), rewriteResult.getKeywords(), topK);
        RetrievalDebugInfo debugInfo = RetrievalDebugInfo.builder()
                .originalQuery(rewriteResult.getOriginalQuery())
                .retrievalQuery(rewriteResult.getRetrievalQuery())
                .alternateQueries(rewriteResult.getAlternateQueries())
                .keywords(rewriteResult.getKeywords())
                .recallSteps(recallSteps)
                .candidateCount(merged.size())
                .rerankedCount(merged.size())
                .selectedCount(reranked.size())
                .build();

        return RetrievalExecution.builder()
                .selectedChunks(reranked)
                .retrievalDebug(debugInfo)
                .build();
    }

    private RecallBatch recall(String source, String query, List<RetrievedChunk> chunks) {
        return new RecallBatch(source, query, chunks == null ? List.of() : chunks);
    }

    private RetrievedChunk mapKeywordChunk(VectorStoreChunkEntity entity) {
        return RetrievedChunk.builder()
                .chunkId(entity.getChunkId())
                .documentId(entity.getDocumentId())
                .chunkIndex(entity.getChunkIndex())
                .filename(entity.getFilename())
                .preview(entity.getPreview())
                .content(entity.getContent())
                .keywordScore(normalizeKeywordScore(entity.getLexicalScore()))
                .build();
    }

    private void mergeCandidates(
            Map<String, RetrievedChunk> merged,
            RecallBatch recallBatch,
            List<String> keywords,
            List<RecallTraceItem> recallSteps) {
        recallSteps.add(RecallTraceItem.builder()
                .source(recallBatch.source())
                .query(recallBatch.query())
                .returnedCount(recallBatch.chunks().size())
                .build());

        int rank = 0;
        for (RetrievedChunk chunk : recallBatch.chunks()) {
            rank++;
            String key = chunkKey(chunk);
            RetrievedChunk current = merged.get(key);
            if (current == null) {
                current = RetrievedChunk.builder()
                        .chunkId(chunk.getChunkId())
                        .documentId(chunk.getDocumentId())
                        .chunkIndex(chunk.getChunkIndex())
                        .filename(chunk.getFilename())
                        .preview(chunk.getPreview())
                        .content(chunk.getContent())
                        .semanticScore(chunk.getSemanticScore())
                        .keywordScore(chunk.getKeywordScore())
                        .recallRank(rank)
                        .build();
                merged.put(key, current);
            } else {
                current.setSemanticScore(Math.max(current.getSemanticScore(), chunk.getSemanticScore()));
                current.setKeywordScore(Math.max(current.getKeywordScore(), chunk.getKeywordScore()));
                if ((current.getPreview() == null || current.getPreview().isBlank()) && chunk.getPreview() != null) {
                    current.setPreview(chunk.getPreview());
                }
                if ((current.getContent() == null || current.getContent().isBlank()) && chunk.getContent() != null) {
                    current.setContent(chunk.getContent());
                }
                current.setRecallRank(Math.min(current.getRecallRank(), rank));
            }

            mergeList(current.getRecallSources(), List.of(recallBatch.source()));
            mergeList(current.getMatchedTerms(), matchTerms(current, keywords));
        }
    }

    private List<String> matchTerms(RetrievedChunk chunk, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        String haystack = String.join(" ",
                        chunk.getFilename() == null ? "" : chunk.getFilename(),
                        chunk.getPreview() == null ? "" : chunk.getPreview(),
                        chunk.getContent() == null ? "" : chunk.getContent())
                .toLowerCase(Locale.ROOT);
        return keywords.stream()
                .filter(term -> haystack.contains(term.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private void mergeList(List<String> target, List<String> additions) {
        if (target == null || additions == null || additions.isEmpty()) {
            return;
        }
        Set<String> values = new LinkedHashSet<>(target);
        values.addAll(additions);
        target.clear();
        target.addAll(values);
    }

    private String chunkKey(RetrievedChunk chunk) {
        if (chunk.getChunkId() != null && !chunk.getChunkId().isBlank()) {
            return chunk.getChunkId();
        }
        return String.valueOf(chunk.getDocumentId()) + ":" + chunk.getChunkIndex() + ":" + chunk.getFilename();
    }

    private double normalizeKeywordScore(double lexicalScore) {
        if (Double.isNaN(lexicalScore) || lexicalScore <= 0) {
            return 0.0d;
        }
        return Math.min(1.0d, lexicalScore / 10.0d);
    }

    private record RecallBatch(String source, String query, List<RetrievedChunk> chunks) {
    }
}
