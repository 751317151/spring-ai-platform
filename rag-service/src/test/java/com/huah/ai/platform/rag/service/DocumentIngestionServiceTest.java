package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.rag.metrics.RagMetricsService;
import com.huah.ai.platform.rag.model.DocumentMeta;
import com.huah.ai.platform.rag.model.KnowledgeBase;
import com.huah.ai.platform.rag.parser.ExcelDocumentParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private DocumentMetaService metaService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ExcelDocumentParser excelParser;

    @Mock
    private RagMetricsService metricsService;

    private DocumentIngestionService service;

    @BeforeEach
    void setUp() {
        service = new DocumentIngestionService(
                vectorStore,
                metaService,
                fileStorageService,
                excelParser,
                metricsService
        );
    }

    @Test
    void ingestDocumentCreatesProcessingThenIndexesDocument() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "sheet-data".getBytes(StandardCharsets.UTF_8)
        );
        KnowledgeBase kb = KnowledgeBase.builder()
                .id("kb-1")
                .chunkSize(1000)
                .chunkOverlap(200)
                .build();
        DocumentMeta indexedMeta = DocumentMeta.builder()
                .id("doc-1")
                .filename("report.xlsx")
                .knowledgeBaseId("kb-1")
                .status(DocumentMeta.STATUS_INDEXED)
                .chunkCount(1)
                .build();

        when(metaService.getKnowledgeBase("kb-1")).thenReturn(kb);
        when(excelParser.parse(any(Resource.class))).thenReturn(List.of(new Document("Quarterly sales data")));
        when(metaService.markDocumentIndexed(any(String.class), any(String.class), any(String.class), eq(1)))
                .thenReturn(indexedMeta);

        DocumentMeta result = service.ingestDocument(file, "kb-1", "user-1");

        ArgumentCaptor<DocumentMeta> processingCaptor = ArgumentCaptor.forClass(DocumentMeta.class);
        ArgumentCaptor<List<Document>> chunkCaptor = ArgumentCaptor.forClass(List.class);

        verify(metaService).createProcessingDocumentMeta(processingCaptor.capture());
        verify(fileStorageService).upload(any(String.class), any(), eq((long) file.getSize()), eq(file.getContentType()));
        verify(vectorStore).add(chunkCaptor.capture());
        verify(metaService).markDocumentIndexed(
                eq(processingCaptor.getValue().getId()),
                eq(processingCaptor.getValue().getKnowledgeBaseId() + "/" + processingCaptor.getValue().getId() + "/report.xlsx"),
                eq(file.getContentType()),
                eq(1)
        );

        assertNotNull(result);
        assertEquals(DocumentMeta.STATUS_PROCESSING, processingCaptor.getValue().getStatus());
        assertEquals(1, chunkCaptor.getValue().size());
        assertEquals("kb-1", chunkCaptor.getValue().get(0).getMetadata().get("kb_id"));
        assertEquals("user-1", chunkCaptor.getValue().get(0).getMetadata().get("uploaded_by"));
        assertEquals("xlsx", chunkCaptor.getValue().get(0).getMetadata().get("file_type"));
    }

    @Test
    void ingestDocumentMarksFailedAndKeepsSourceWhenParsingFailsAfterUpload() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "sheet-data".getBytes(StandardCharsets.UTF_8)
        );
        KnowledgeBase kb = KnowledgeBase.builder()
                .id("kb-1")
                .chunkSize(1000)
                .chunkOverlap(200)
                .build();

        when(metaService.getKnowledgeBase("kb-1")).thenReturn(kb);
        when(excelParser.parse(any(Resource.class))).thenThrow(new BizException("解析失败"));

        BizException exception = assertThrows(BizException.class, () -> service.ingestDocument(file, "kb-1", "user-1"));

        ArgumentCaptor<DocumentMeta> processingCaptor = ArgumentCaptor.forClass(DocumentMeta.class);
        verify(metaService).createProcessingDocumentMeta(processingCaptor.capture());
        verify(metaService).markDocumentFailed(eq(processingCaptor.getValue().getId()), eq("解析失败"));
        assertEquals("解析失败", exception.getMessage());
    }

    @Test
    void retryDocumentDownloadsSourceAndReindexesFailedDocument() {
        DocumentMeta failedDoc = DocumentMeta.builder()
                .id("doc-1")
                .filename("report.xlsx")
                .knowledgeBaseId("kb-1")
                .storagePath("kb-1/doc-1/report.xlsx")
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .uploadedBy("user-1")
                .status(DocumentMeta.STATUS_PROCESSING)
                .build();
        KnowledgeBase kb = KnowledgeBase.builder()
                .id("kb-1")
                .chunkSize(1000)
                .chunkOverlap(200)
                .build();
        DocumentMeta indexedMeta = DocumentMeta.builder()
                .id("doc-1")
                .status(DocumentMeta.STATUS_INDEXED)
                .chunkCount(1)
                .build();

        when(metaService.resetFailedDocumentForRetry("doc-1")).thenReturn(failedDoc);
        when(metaService.getKnowledgeBase("kb-1")).thenReturn(kb);
        when(fileStorageService.download("kb-1/doc-1/report.xlsx"))
                .thenReturn(new ByteArrayInputStream("sheet-data".getBytes(StandardCharsets.UTF_8)));
        when(excelParser.parse(any(Resource.class))).thenReturn(List.of(new Document("Quarterly sales data")));
        when(metaService.markDocumentIndexed("doc-1", "kb-1/doc-1/report.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 1)).thenReturn(indexedMeta);

        DocumentMeta result = service.retryDocument("doc-1");

        verify(fileStorageService).download("kb-1/doc-1/report.xlsx");
        verify(vectorStore).add(any());
        assertEquals(DocumentMeta.STATUS_INDEXED, result.getStatus());
    }
}
