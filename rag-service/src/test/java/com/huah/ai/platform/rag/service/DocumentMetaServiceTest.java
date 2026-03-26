package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.rag.mapper.DocumentMetaMapper;
import com.huah.ai.platform.rag.mapper.KnowledgeBaseMapper;
import com.huah.ai.platform.rag.metrics.RagMetricsService;
import com.huah.ai.platform.rag.model.DocumentChunkPreview;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        KnowledgeBase kb = KnowledgeBase.builder().id("kb-1").name("test").build();
        when(kbMapper.selectById("kb-1")).thenReturn(kb);
        when(docMapper.selectByKnowledgeBaseId("kb-1")).thenReturn(List.of(DocumentMeta.builder().id("doc-1").build()));

        BizException exception = assertThrows(BizException.class, () -> service.deleteKnowledgeBase("kb-1"));

        assertEquals("Knowledge base still contains 1 documents. Delete them first.", exception.getMessage());
        verify(kbMapper, never()).deleteById("kb-1");
    }

    @Test
    void markDocumentIndexedTransitionsStateAndUpdatesKnowledgeBaseCounters() {
        DocumentMeta doc = DocumentMeta.builder().id("doc-1").knowledgeBaseId("kb-1").status(DocumentMeta.STATUS_PROCESSING).chunkCount(0).build();
        KnowledgeBase kb = KnowledgeBase.builder().id("kb-1").documentCount(5).totalChunks(20).build();
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
        DocumentMeta doc = DocumentMeta.builder().id("doc-1").status(DocumentMeta.STATUS_PROCESSING).build();
        when(docMapper.selectById("doc-1")).thenReturn(doc);

        DocumentMeta result = service.markDocumentFailed("doc-1", "vectorization failed");

        verify(docMapper).updateById(doc);
        assertEquals(DocumentMeta.STATUS_FAILED, result.getStatus());
        assertEquals("vectorization failed", result.getErrorMessage());
        assertNull(result.getIndexedAt());
    }

    @Test
    void resetFailedDocumentForRetryMovesStateBackToProcessing() {
        DocumentMeta doc = DocumentMeta.builder().id("doc-1").status(DocumentMeta.STATUS_FAILED).storagePath("kb-1/doc-1/test.pdf").chunkCount(5).errorMessage("old error").build();
        when(docMapper.selectById("doc-1")).thenReturn(doc);

        DocumentMeta result = service.resetFailedDocumentForRetry("doc-1");

        verify(docMapper).updateById(doc);
        assertEquals(DocumentMeta.STATUS_PROCESSING, result.getStatus());
        assertEquals(0, result.getChunkCount());
        assertNull(result.getErrorMessage());
    }

    @Test
    void findLatestByKnowledgeBaseAndFilenameDelegatesToMapper() {
        DocumentMeta doc = DocumentMeta.builder().id("doc-1").filename("test.pdf").build();
        when(docMapper.selectLatestByKnowledgeBaseIdAndFilename("kb-1", "test.pdf")).thenReturn(doc);

        DocumentMeta result = service.findLatestByKnowledgeBaseAndFilename("kb-1", "test.pdf");

        assertEquals(doc, result);
    }

    @Test
    void deleteDocumentRemovesStorageVectorsAndMetadata() {
        DocumentMeta doc = DocumentMeta.builder().id("doc-1").filename("test.pdf").knowledgeBaseId("kb-1").storagePath("kb-1/doc-1/test.pdf").chunkCount(3).status(DocumentMeta.STATUS_INDEXED).build();
        KnowledgeBase kb = KnowledgeBase.builder().id("kb-1").documentCount(5).totalChunks(20).build();

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

    @Test
    void prepareDocumentForReindexClearsVectorsAndResetsIndexedDocument() {
        DocumentMeta doc = DocumentMeta.builder().id("doc-1").knowledgeBaseId("kb-1").storagePath("kb-1/doc-1/test.pdf").status(DocumentMeta.STATUS_INDEXED).chunkCount(3).errorMessage("old").build();
        KnowledgeBase kb = KnowledgeBase.builder().id("kb-1").documentCount(5).totalChunks(20).build();

        when(docMapper.selectById("doc-1")).thenReturn(doc);
        when(kbMapper.selectById("kb-1")).thenReturn(kb);

        DocumentMeta result = service.prepareDocumentForReindex("doc-1");

        verify(jdbcTemplate).update("DELETE FROM vector_store WHERE metadata->>'doc_id' = ?", "doc-1");
        verify(kbMapper).updateById(kb);
        verify(docMapper).updateById(doc);
        assertEquals(DocumentMeta.STATUS_PROCESSING, result.getStatus());
        assertEquals(0, result.getChunkCount());
        assertNull(result.getErrorMessage());
        assertEquals(4, kb.getDocumentCount());
        assertEquals(17, kb.getTotalChunks());
    }

    @Test
    void listDocumentChunksReturnsChunkPreviewsOrderedFromVectorStore() {
        DocumentMeta doc = DocumentMeta.builder().id("doc-1").build();
        List<DocumentChunkPreview> expected = List.of(DocumentChunkPreview.builder().id("chunk-1").chunkIndex(1).preview("preview").content("full content").charCount(12).build());

        when(docMapper.selectById("doc-1")).thenReturn(doc);
        when(jdbcTemplate.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq("doc-1"))).thenReturn(expected);

        List<DocumentChunkPreview> result = service.listDocumentChunks("doc-1");

        assertEquals(expected, result);
        verify(jdbcTemplate).query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq("doc-1"));
    }

    @Test
    void listKnowledgeBasesFiltersByVisibilityScopeForNonAdminUsers() {
        when(kbMapper.selectList(null)).thenReturn(List.of(
                KnowledgeBase.builder().id("kb-public").name("public").department("").visibilityScope("PUBLIC").build(),
                KnowledgeBase.builder().id("kb-rd").name("rd").department("研发中心").visibilityScope("DEPARTMENT").build(),
                KnowledgeBase.builder().id("kb-sales").name("sales").department("销售中心").visibilityScope("DEPARTMENT").build(),
                KnowledgeBase.builder().id("kb-own").name("own").department("财务中心").createdBy("alice").visibilityScope("PRIVATE").build()
        ));

        List<KnowledgeBase> result = service.listKnowledgeBases(new DocumentMetaService.AccessContext("alice", "研发中心", false));

        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(kb -> "kb-public".equals(kb.getId())));
        assertTrue(result.stream().anyMatch(kb -> "kb-rd".equals(kb.getId())));
        assertTrue(result.stream().anyMatch(kb -> "kb-own".equals(kb.getId())));
    }

    @Test
    void getKnowledgeBaseRejectsCrossDepartmentAccessForNonAdminUsers() {
        KnowledgeBase kb = KnowledgeBase.builder().id("kb-2").department("销售中心").createdBy("bob").visibilityScope("DEPARTMENT").build();
        when(kbMapper.selectById("kb-2")).thenReturn(kb);

        Exception exception = assertThrows(Exception.class, () ->
                service.getKnowledgeBase("kb-2", new DocumentMetaService.AccessContext("alice", "研发中心", false)));

        assertEquals("无权限访问该知识库", exception.getMessage());
    }

    @Test
    void privateKnowledgeBaseOnlyVisibleToCreator() {
        KnowledgeBase kb = KnowledgeBase.builder().id("kb-3").department("研发中心").createdBy("bob").visibilityScope("PRIVATE").build();
        when(kbMapper.selectById("kb-3")).thenReturn(kb);

        Exception exception = assertThrows(Exception.class, () ->
                service.getKnowledgeBase("kb-3", new DocumentMetaService.AccessContext("alice", "研发中心", false)));

        assertEquals("无权限访问该知识库", exception.getMessage());
    }
    @Test
    void updateKnowledgeBaseAllowsUpdatingVisibilityScope() {
        KnowledgeBase existing = KnowledgeBase.builder()
                .id("kb-1")
                .name("研发规范")
                .visibilityScope("DEPARTMENT")
                .status("ACTIVE")
                .build();
        KnowledgeBase update = KnowledgeBase.builder()
                .visibilityScope("PRIVATE")
                .status("DISABLED")
                .build();
        when(kbMapper.selectById("kb-1")).thenReturn(existing);

        KnowledgeBase result = service.updateKnowledgeBase("kb-1", update);

        verify(kbMapper).updateById(existing);
        assertEquals("PRIVATE", result.getVisibilityScope());
        assertEquals("DISABLED", result.getStatus());
    }
    @Test
    void updateKnowledgeBaseAllowsUpdatingChunkStrategyAndStructuredBatchSize() {
        KnowledgeBase existing = KnowledgeBase.builder()
                .id("kb-1")
                .chunkStrategy("TOKEN")
                .structuredBatchSize(20)
                .build();
        KnowledgeBase update = KnowledgeBase.builder()
                .chunkStrategy("structured")
                .structuredBatchSize(8)
                .build();
        when(kbMapper.selectById("kb-1")).thenReturn(existing);

        KnowledgeBase result = service.updateKnowledgeBase("kb-1", update);

        verify(kbMapper).updateById(existing);
        assertEquals("STRUCTURED", result.getChunkStrategy());
        assertEquals(8, result.getStructuredBatchSize());
    }
}
