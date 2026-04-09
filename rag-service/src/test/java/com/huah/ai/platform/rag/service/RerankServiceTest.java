package com.huah.ai.platform.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.huah.ai.platform.rag.model.RetrievedChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class RerankServiceTest {

    private final RerankService service = new RerankService();

    @Test
    void rerankPrefersChunkWithBetterSemanticAndKeywordCoverage() {
        RetrievedChunk strong = RetrievedChunk.builder()
                .filename("报销制度.md")
                .content("员工报销流程与审批规则")
                .semanticScore(0.92d)
                .keywordScore(0.60d)
                .matchedTerms(List.of("员工", "报销"))
                .recallSources(List.of("vector-original", "keyword"))
                .build();
        RetrievedChunk weak = RetrievedChunk.builder()
                .filename("其他文档.md")
                .content("与问题弱相关")
                .semanticScore(0.35d)
                .keywordScore(0.10d)
                .matchedTerms(List.of("报销"))
                .recallSources(List.of("vector-original"))
                .build();

        List<RetrievedChunk> result = service.rerank(List.of(weak, strong), List.of("员工", "报销"), 2);

        assertEquals("报销制度.md", result.get(0).getFilename());
        assertEquals(2, result.size());
    }
}
