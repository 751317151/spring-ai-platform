package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.rag.model.RetrievedChunk;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class RerankService {

    public List<RetrievedChunk> rerank(List<RetrievedChunk> candidates, List<String> keywords, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        int selectedTopK = Math.max(1, topK);
        for (RetrievedChunk candidate : candidates) {
            candidate.setRerankScore(score(candidate, keywords));
        }

        return candidates.stream()
                .sorted(Comparator.comparingDouble(RetrievedChunk::getRerankScore).reversed()
                        .thenComparing(chunk -> chunk.getFilename() == null ? "" : chunk.getFilename())
                        .thenComparing(chunk -> chunk.getChunkIndex() == null ? Integer.MAX_VALUE : chunk.getChunkIndex()))
                .limit(selectedTopK)
                .toList();
    }

    private double score(RetrievedChunk candidate, List<String> keywords) {
        double semanticScore = clamp(candidate.getSemanticScore());
        double keywordScore = clamp(candidate.getKeywordScore());
        double keywordCoverage = computeKeywordCoverage(candidate, keywords);
        double multiPathBoost = candidate.getRecallSources() == null
                ? 0.0d
                : Math.min(0.18d, Math.max(0, candidate.getRecallSources().size() - 1) * 0.08d);
        double filenameBoost = computeFilenameBoost(candidate, keywords);
        return clamp(semanticScore * 0.52d
                + keywordScore * 0.23d
                + keywordCoverage * 0.17d
                + multiPathBoost
                + filenameBoost);
    }

    private double computeKeywordCoverage(RetrievedChunk candidate, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return 0.0d;
        }
        int matchedCount = candidate.getMatchedTerms() == null ? 0 : candidate.getMatchedTerms().size();
        return clamp((double) matchedCount / (double) keywords.size());
    }

    private double computeFilenameBoost(RetrievedChunk candidate, List<String> keywords) {
        if (candidate.getFilename() == null || candidate.getFilename().isBlank() || keywords == null || keywords.isEmpty()) {
            return 0.0d;
        }
        String filename = candidate.getFilename().toLowerCase(Locale.ROOT);
        return keywords.stream()
                .map(term -> term.toLowerCase(Locale.ROOT))
                .anyMatch(filename::contains) ? 0.08d : 0.0d;
    }

    private double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
