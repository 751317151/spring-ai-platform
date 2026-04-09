package com.huah.ai.platform.rag.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huah.ai.platform.rag.model.RetrievalRewriteResult;
import org.junit.jupiter.api.Test;

class QueryRewriteServiceTest {

    private final QueryRewriteService service = new QueryRewriteService();

    @Test
    void rewriteRemovesPolitePrefixAndExtractsKeywords() {
        RetrievalRewriteResult result = service.rewrite("请帮我说明一下员工报销流程是什么");

        assertTrue(result.getRetrievalQuery().contains("员工报销流程"));
        assertFalse(result.getKeywords().isEmpty());
        assertTrue(result.getKeywords().stream().anyMatch(keyword -> keyword.contains("员工报销流程")));
    }
}
