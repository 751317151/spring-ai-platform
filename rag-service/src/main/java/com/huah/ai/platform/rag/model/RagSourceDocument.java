package com.huah.ai.platform.rag.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RagSourceDocument {
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
