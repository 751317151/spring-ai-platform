package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.common.exception.PermissionDeniedException;
import com.huah.ai.platform.rag.mapper.DocumentMetaMapper;
import com.huah.ai.platform.rag.mapper.KnowledgeBaseMapper;
import com.huah.ai.platform.rag.metrics.RagMetricsService;
import com.huah.ai.platform.rag.model.DocumentChunkPreview;
import com.huah.ai.platform.rag.model.DocumentMetaEntity;
import com.huah.ai.platform.rag.model.KnowledgeBaseEntity;
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
        KnowledgeBaseEntity kb = KnowledgeBaseEntity.builder().id(1001L).name("test").build();
        when(kbMapper.selectById(1001L)).thenReturn(kb);
        when(docMapper.selectByKnowledgeBaseId(1001L)).thenReturn(List.of(DocumentMetaEntity.builder().id(2001L).build()));

        BizException exception = assertThrows(BizException.class, () -> service.deleteKnowledgeBase(1001L));

        assertEquals("Knowledge base still contains 1 documents. Delete them first.", exception.getMessage());
        verify(kbMapper, never()).deleteById(1001L);
    }

    @Test
    void markDocumentIndexedTransitionsStateAndUpdatesKnowledgeBaseCounters() {
        DocumentMetaEntity doc = DocumentMetaEntity.builder().id(2001L).knowledgeBaseId(1001L).status(DocumentMetaEntity.STATUS_PROCESSING).chunkCount(0).build();
        KnowledgeBaseEntity kb = KnowledgeBaseEntity.builder().id(1001L).documentCount(5).totalChunks(20).build();
        when(docMapper.selectById(2001L)).thenReturn(doc);
        when(kbMapper.selectById(1001L)).thenReturn(kb);

        DocumentMetaEntity result = service.markDocumentIndexed(2001L, "1001/2001/test.pdf", "application/pdf", 3);

        verify(docMapper).updateById(doc);
        verify(kbMapper).updateById(kb);
        assertEquals(DocumentMetaEntity.STATUS_INDEXED, result.getStatus());
        assertEquals("1001/2001/test.pdf", result.getStoragePath());
        assertEquals(3, result.getChunkCount());
        assertEquals(6, kb.getDocumentCount());
        assertEquals(23, kb.getTotalChunks());
    }

    @Test
    void markDocumentFailedTransitionsStateWithoutIndexTimestamp() {
        DocumentMetaEntity doc = DocumentMetaEntity.builder().id(2001L).status(DocumentMetaEntity.STATUS_PROCESSING).build();
        when(docMapper.selectById(2001L)).thenReturn(doc);

        DocumentMetaEntity result = service.markDocumentFailed(2001L, "vectorization failed");

        verify(docMapper).updateById(doc);
        assertEquals(DocumentMetaEntity.STATUS_FAILED, result.getStatus());
        assertEquals("vectorization failed", result.getErrorMessage());
        assertNull(result.getIndexedAt());
    }

    @Test
    void resetFailedDocumentForRetryMovesStateBackToProcessing() {
        DocumentMetaEntity doc = DocumentMetaEntity.builder()
                .id(2001L)
                .status(DocumentMetaEntity.STATUS_FAILED)
                .storagePath("1001/2001/test.pdf")
                .chunkCount(5)
                .errorMessage("old error")
                .build();
        when(docMapper.selectById(2001L)).thenReturn(doc);

        DocumentMetaEntity result = service.resetFailedDocumentForRetry(2001L);

        verify(docMapper).updateById(doc);
        assertEquals(DocumentMetaEntity.STATUS_PROCESSING, result.getStatus());
        assertEquals(0, result.getChunkCount());
        assertNull(result.getErrorMessage());
    }

    @Test
    void findLatestByKnowledgeBaseAndFilenameDelegatesToMapper() {
        DocumentMetaEntity doc = DocumentMetaEntity.builder().id(2001L).filename("test.pdf").build();
        when(docMapper.selectLatestByKnowledgeBaseIdAndFilename(1001L, "test.pdf")).thenReturn(doc);

        DocumentMetaEntity result = service.findLatestByKnowledgeBaseAndFilename(1001L, "test.pdf");

        assertEquals(doc, result);
    }

    @Test
    void deleteDocumentRemovesStorageVectorsAndMetadata() {
        DocumentMetaEntity doc = DocumentMetaEntity.builder()
                .id(2001L)
                .filename("test.pdf")
                .knowledgeBaseId(1001L)
                .storagePath("1001/2001/test.pdf")
                .chunkCount(3)
                .status(DocumentMetaEntity.STATUS_INDEXED)
                .build();
        KnowledgeBaseEntity kb = KnowledgeBaseEntity.builder().id(1001L).documentCount(5).totalChunks(20).build();

        when(docMapper.selectById(2001L)).thenReturn(doc);
        when(kbMapper.selectById(1001L)).thenReturn(kb);

        service.deleteDocument(2001L);

        verify(fileStorageService).delete("1001/2001/test.pdf");
        verify(jdbcTemplate).update("DELETE FROM vector_store WHERE metadata->>'doc_id' = ?", "2001");
        verify(kbMapper).updateById(kb);
        verify(docMapper).deleteById(2001L);
        assertEquals(4, kb.getDocumentCount());
        assertEquals(17, kb.getTotalChunks());
    }

    @Test
    void prepareDocumentForReindexClearsVectorsAndResetsIndexedDocument() {
        DocumentMetaEntity doc = DocumentMetaEntity.builder()
                .id(2001L)
                .knowledgeBaseId(1001L)
                .storagePath("1001/2001/test.pdf")
                .status(DocumentMetaEntity.STATUS_INDEXED)
                .chunkCount(3)
                .errorMessage("old")
                .build();
        KnowledgeBaseEntity kb = KnowledgeBaseEntity.builder().id(1001L).documentCount(5).totalChunks(20).build();

        when(docMapper.selectById(2001L)).thenReturn(doc);
        when(kbMapper.selectById(1001L)).thenReturn(kb);

        DocumentMetaEntity result = service.prepareDocumentForReindex(2001L);

        verify(jdbcTemplate).update("DELETE FROM vector_store WHERE metadata->>'doc_id' = ?", "2001");
        verify(kbMapper).updateById(kb);
        verify(docMapper).updateById(doc);
        assertEquals(DocumentMetaEntity.STATUS_PROCESSING, result.getStatus());
        assertEquals(0, result.getChunkCount());
        assertNull(result.getErrorMessage());
        assertEquals(4, kb.getDocumentCount());
        assertEquals(17, kb.getTotalChunks());
    }

    @Test
    void listDocumentChunksReturnsChunkPreviewsOrderedFromVectorStore() {
        DocumentMetaEntity doc = DocumentMetaEntity.builder().id(2001L).build();
        List<DocumentChunkPreview> expected = List.of(
                DocumentChunkPreview.builder().id("chunk-1").chunkIndex(1).preview("preview").content("full content").charCount(12).build()
        );

        when(docMapper.selectById(2001L)).thenReturn(doc);
        when(jdbcTemplate.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq("2001"))).thenReturn(expected);

        List<DocumentChunkPreview> result = service.listDocumentChunks(2001L);

        assertEquals(expected, result);
        verify(jdbcTemplate).query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq("2001"));
    }

    @Test
    void listKnowledgeBasesFiltersByVisibilityScopeForNonAdminUsers() {
        when(kbMapper.selectList(null)).thenReturn(List.of(
                KnowledgeBaseEntity.builder().id(1001L).name("public").department("").visibilityScope("PUBLIC").build(),
                KnowledgeBaseEntity.builder().id(1002L).name("rd").department("研发中心").visibilityScope("DEPARTMENT").build(),
                KnowledgeBaseEntity.builder().id(1003L).name("sales").department("销售中心").visibilityScope("DEPARTMENT").build(),
                KnowledgeBaseEntity.builder().id(1004L).name("own").department("财务中心").createdBy("alice").visibilityScope("PRIVATE").build()
        ));

        List<KnowledgeBaseEntity> result = service.listKnowledgeBases(new DocumentMetaService.AccessContext("alice", "研发中心", false));

        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(kb -> 1001L == kb.getId()));
        assertTrue(result.stream().anyMatch(kb -> 1002L == kb.getId()));
        assertTrue(result.stream().anyMatch(kb -> 1004L == kb.getId()));
    }

    @Test
    void getKnowledgeBaseRejectsCrossDepartmentAccessForNonAdminUsers() {
        KnowledgeBaseEntity kb = KnowledgeBaseEntity.builder().id(1002L).department("销售中心").createdBy("bob").visibilityScope("DEPARTMENT").build();
        when(kbMapper.selectById(1002L)).thenReturn(kb);

        PermissionDeniedException exception = assertThrows(PermissionDeniedException.class, () ->
                service.getKnowledgeBase(1002L, new DocumentMetaService.AccessContext("alice", "研发中心", false)));

        assertTrue(exception.getMessage() != null && !exception.getMessage().isBlank());
    }

    @Test
    void privateKnowledgeBaseOnlyVisibleToCreator() {
        KnowledgeBaseEntity kb = KnowledgeBaseEntity.builder().id(1003L).department("研发中心").createdBy("bob").visibilityScope("PRIVATE").build();
        when(kbMapper.selectById(1003L)).thenReturn(kb);

        PermissionDeniedException exception = assertThrows(PermissionDeniedException.class, () ->
                service.getKnowledgeBase(1003L, new DocumentMetaService.AccessContext("alice", "研发中心", false)));

        assertTrue(exception.getMessage() != null && !exception.getMessage().isBlank());
    }

    @Test
    void updateKnowledgeBaseAllowsUpdatingVisibilityScope() {
        KnowledgeBaseEntity existing = KnowledgeBaseEntity.builder()
                .id(1001L)
                .name("研发规范")
                .visibilityScope("DEPARTMENT")
                .status("ACTIVE")
                .build();
        KnowledgeBaseEntity update = KnowledgeBaseEntity.builder()
                .visibilityScope("PRIVATE")
                .status("DISABLED")
                .build();
        when(kbMapper.selectById(1001L)).thenReturn(existing);

        KnowledgeBaseEntity result = service.updateKnowledgeBase(1001L, update);

        verify(kbMapper).updateById(existing);
        assertEquals("PRIVATE", result.getVisibilityScope());
        assertEquals("DISABLED", result.getStatus());
    }

    @Test
    void updateKnowledgeBaseAllowsUpdatingChunkStrategyAndStructuredBatchSize() {
        KnowledgeBaseEntity existing = KnowledgeBaseEntity.builder()
                .id(1001L)
                .chunkStrategy("TOKEN")
                .structuredBatchSize(20)
                .build();
        KnowledgeBaseEntity update = KnowledgeBaseEntity.builder()
                .chunkStrategy("structured")
                .structuredBatchSize(8)
                .build();
        when(kbMapper.selectById(1001L)).thenReturn(existing);

        KnowledgeBaseEntity result = service.updateKnowledgeBase(1001L, update);

        verify(kbMapper).updateById(existing);
        assertEquals("STRUCTURED", result.getChunkStrategy());
        assertEquals(8, result.getStructuredBatchSize());
    }
}
