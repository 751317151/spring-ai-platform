package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.rag.mapper.DocumentMetaMapper;
import com.huah.ai.platform.rag.mapper.KnowledgeBaseMapper;
import com.huah.ai.platform.rag.metrics.RagMetricsService;
import com.huah.ai.platform.rag.model.DocumentMeta;
import com.huah.ai.platform.rag.model.KnowledgeBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentMetaServiceTest {

    @Mock
    private DocumentMetaMapper docMapper;

    @Mock
    private KnowledgeBaseMapper kbMapper;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private RagMetricsService metricsService;

    private DocumentMetaService service;

    @BeforeEach
    void setUp() {
        service = new DocumentMetaService(docMapper, kbMapper, fileStorageService, jdbcTemplate, metricsService);
    }

    @Test
    void deleteKnowledgeBaseRejectsWhenDocumentsStillExist() {
        KnowledgeBase kb = KnowledgeBase.builder().id("kb-1").name("测试库").build();
        when(kbMapper.selectById("kb-1")).thenReturn(kb);
        when(docMapper.selectByKnowledgeBaseId("kb-1")).thenReturn(List.of(
                DocumentMeta.builder().id("doc-1").build()
        ));

        BizException exception = assertThrows(BizException.class, () -> service.deleteKnowledgeBase("kb-1"));

        assertEquals("知识库下还有 1 个文档，请先删除文档。", exception.getMessage());
        verify(kbMapper, never()).deleteById("kb-1");
    }

    @Test
    void markDocumentIndexedTransitionsStateAndUpdatesKnowledgeBaseCounters() {
        DocumentMeta doc = DocumentMeta.builder()
                .id("doc-1")
                .knowledgeBaseId("kb-1")
                .status(DocumentMeta.STATUS_PROCESSING)
                .chunkCount(0)
                .build();
        KnowledgeBase kb = KnowledgeBase.builder()
                .id("kb-1")
                .documentCount(5)
                .totalChunks(20)
                .build();
        when(docMapper.selectById("doc-1")).thenReturn(doc);
        when(kbMapper.selectById("kb-1")).thenReturn(kb);

        DocumentMeta result = service.markDocumentIndexed("doc-1", "kb-1/doc-1/test.pdf", "application/pdf", 3);

        verify(docMapper).updateById(doc);
        verify(kbMapper).updateById(kb);
        assertEquals(DocumentMeta.STATUS_INDEXED, result.getStatus());
        assertEquals("kb-1/doc-1/test.pdf", result.getStoragePath());
        assertEquals(3, result.getChunkCount());
        assertEquals(6, kb.getDocumentCount());
        assertEquals(23, kb.getTotalChunks());
    }

    @Test
    void markDocumentFailedTransitionsStateWithoutIndexTimestamp() {
        DocumentMeta doc = DocumentMeta.builder()
                .id("doc-1")
                .status(DocumentMeta.STATUS_PROCESSING)
                .build();
        when(docMapper.selectById("doc-1")).thenReturn(doc);

        DocumentMeta result = service.markDocumentFailed("doc-1", "向量化失败");

        verify(docMapper).updateById(doc);
        assertEquals(DocumentMeta.STATUS_FAILED, result.getStatus());
        assertEquals("向量化失败", result.getErrorMessage());
        assertNull(result.getIndexedAt());
    }

    @Test
    void resetFailedDocumentForRetryMovesStateBackToProcessing() {
        DocumentMeta doc = DocumentMeta.builder()
                .id("doc-1")
                .status(DocumentMeta.STATUS_FAILED)
                .storagePath("kb-1/doc-1/test.pdf")
                .chunkCount(5)
                .errorMessage("旧错误")
                .build();
        when(docMapper.selectById("doc-1")).thenReturn(doc);

        DocumentMeta result = service.resetFailedDocumentForRetry("doc-1");

        verify(docMapper).updateById(doc);
        assertEquals(DocumentMeta.STATUS_PROCESSING, result.getStatus());
        assertEquals(0, result.getChunkCount());
        assertNull(result.getErrorMessage());
    }

    @Test
    void deleteDocumentRemovesStorageVectorsAndMetadata() {
        DocumentMeta doc = DocumentMeta.builder()
                .id("doc-1")
                .filename("test.pdf")
                .knowledgeBaseId("kb-1")
                .storagePath("kb-1/doc-1/test.pdf")
                .chunkCount(3)
                .status(DocumentMeta.STATUS_INDEXED)
                .build();
        KnowledgeBase kb = KnowledgeBase.builder()
                .id("kb-1")
                .documentCount(5)
                .totalChunks(20)
                .build();

        when(docMapper.selectById("doc-1")).thenReturn(doc);
        when(kbMapper.selectById("kb-1")).thenReturn(kb);

        service.deleteDocument("doc-1");

        verify(fileStorageService).delete("kb-1/doc-1/test.pdf");
        verify(jdbcTemplate).update("DELETE FROM vector_store WHERE metadata->>'doc_id' = ?", "doc-1");
        verify(kbMapper).updateById(kb);
        verify(docMapper).deleteById("doc-1");
        assertEquals(4, kb.getDocumentCount());
        assertEquals(17, kb.getTotalChunks());
    }
}
