package com.huah.ai.platform.rag.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievedChunk {

    private Long documentId;

    private String chunkId;

    private Integer chunkIndex;

    private String filename;

    private String preview;

    private String content;

    @Builder.Default
    private double semanticScore = 0.0d;

    @Builder.Default
    private double keywordScore = 0.0d;

    @Builder.Default
    private double rerankScore = 0.0d;

    @Builder.Default
    private List<String> recallSources = new ArrayList<>();

    @Builder.Default
    private List<String> matchedTerms = new ArrayList<>();

    private int recallRank;
}
