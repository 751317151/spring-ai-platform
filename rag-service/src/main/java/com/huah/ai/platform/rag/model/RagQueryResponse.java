package com.huah.ai.platform.rag.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RagQueryResponse {
    private Long responseId;
    private String answer;
    private List<SourceDocument> sources;
    private long latencyMs;
    private RetrievalDebugInfo retrievalDebug;

    @Data
    @Builder
    public static class SourceDocument {
        private Long documentId;
        private String chunkId;
        private Integer chunkIndex;
        private String filename;
        private String preview;
        private String content;
        private double score;
        private double semanticScore;
        private double keywordScore;
        private List<String> recallSources;
        private List<String> matchedTerms;
    }
}
