package com.huah.ai.platform.rag.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RagQueryResponse {
    private String answer;
    private List<SourceDocument> sources;
    private long latencyMs;

    @Data
    @Builder
    public static class SourceDocument {
        private String filename;
        private String content;
        private double score;
    }
}
