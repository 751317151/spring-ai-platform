package com.huah.ai.platform.rag.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalDebugInfo {

    private String originalQuery;

    private String retrievalQuery;

    private List<String> alternateQueries;

    private List<String> keywords;

    private List<RecallTraceItem> recallSteps;

    private int candidateCount;

    private int rerankedCount;

    private int selectedCount;
}
