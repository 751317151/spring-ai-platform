package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.rag.metrics.RagMetricsService;
import com.huah.ai.platform.rag.model.DocumentMeta;
import com.huah.ai.platform.rag.model.KnowledgeBase;
import com.huah.ai.platform.rag.parser.ExcelDocumentParser;
import com.huah.ai.platform.rag.parser.StructuredDocumentParser;
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
import static org.mockito.Mockito.never;
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
    private StructuredDocumentParser structuredParser;

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
                structuredParser,
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

        when(metaService.findLatestByKnowledgeBaseAndFilename("kb-1", "report.xlsx")).thenReturn(null);
        when(metaService.getKnowledgeBase("kb-1")).thenReturn(kb);
        when(excelParser.parse(any(Resource.class))).thenReturn(List.of(new Document("Quarterly sales data")));
        when(metaService.markDocumentIndexed(any(String.class), any(String.class), any(String.class), eq(1)))
                .thenReturn(indexedMeta);

        DocumentMeta result = service.ingestDocument(file, "kb-1", "user-1", false);

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
        assertEquals(1, chunkCaptor.getValue().get(0).getMetadata().get("chunk_index"));
        assertEquals("Quarterly sales data", chunkCaptor.getValue().get(0).getMetadata().get("chunk_preview"));
        assertEquals(20, chunkCaptor.getValue().get(0).getMetadata().get("char_count"));
    }

    @Test
    void ingestDocumentRejectsDuplicateWhenReplacementDisabled() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "sheet-data".getBytes(StandardCharsets.UTF_8)
        );
        when(metaService.findLatestByKnowledgeBaseAndFilename("kb-1", "report.xlsx"))
                .thenReturn(DocumentMeta.builder().id("existing-doc").filename("report.xlsx").build());

        BizException exception = assertThrows(BizException.class,
                () -> service.ingestDocument(file, "kb-1", "user-1", false));

        assertEquals("A document with the same filename already exists in this knowledge base. Enable replacement or delete it first.", exception.getMessage());
        verify(metaService, never()).createProcessingDocumentMeta(any());
    }

    @Test
    void ingestDocumentDeletesDuplicateWhenReplacementEnabled() {
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
                .status(DocumentMeta.STATUS_INDEXED)
                .chunkCount(1)
                .build();

        when(metaService.findLatestByKnowledgeBaseAndFilename("kb-1", "report.xlsx"))
                .thenReturn(DocumentMeta.builder().id("existing-doc").filename("report.xlsx").build());
        when(metaService.getKnowledgeBase("kb-1")).thenReturn(kb);
        when(excelParser.parse(any(Resource.class))).thenReturn(List.of(new Document("Quarterly sales data")));
        when(metaService.markDocumentIndexed(any(String.class), any(String.class), any(String.class), eq(1)))
                .thenReturn(indexedMeta);

        service.ingestDocument(file, "kb-1", "user-1", true);

        verify(metaService).deleteDocument("existing-doc");
    }

    @Test
    void ingestDocumentParsesCsvAndIndexesDocument() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "dataset.csv",
                "text/csv",
                "name,score\nalice,98".getBytes(StandardCharsets.UTF_8)
        );
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

        when(metaService.findLatestByKnowledgeBaseAndFilename("kb-1", "dataset.csv")).thenReturn(null);
        when(metaService.getKnowledgeBase("kb-1")).thenReturn(kb);
        when(structuredParser.parseCsv(any(Resource.class))).thenReturn(List.of(new Document("Row 1: name=alice; score=98;")));
        when(metaService.markDocumentIndexed(any(String.class), any(String.class), any(String.class), eq(1)))
                .thenReturn(indexedMeta);

        DocumentMeta result = service.ingestDocument(file, "kb-1", "user-1", false);

        assertEquals(DocumentMeta.STATUS_INDEXED, result.getStatus());
        verify(vectorStore).add(any());
    }

    @Test
    void ingestDocumentUsesStructuredChunkStrategyForStructuredFiles() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "dataset.json",
                "application/json",
                "{\"items\":[1,2,3]}".getBytes(StandardCharsets.UTF_8)
        );
        KnowledgeBase kb = KnowledgeBase.builder()
                .id("kb-1")
                .chunkStrategy("STRUCTURED")
                .structuredBatchSize(2)
                .build();
        DocumentMeta indexedMeta = DocumentMeta.builder()
                .id("doc-1")
                .status(DocumentMeta.STATUS_INDEXED)
                .chunkCount(2)
                .build();

        when(metaService.findLatestByKnowledgeBaseAndFilename("kb-1", "dataset.json")).thenReturn(null);
        when(metaService.getKnowledgeBase("kb-1")).thenReturn(kb);
        when(structuredParser.parseJson(any(Resource.class))).thenReturn(List.of(
                new Document("root.items[0] = 1", java.util.Map.of("structured_type", "json")),
                new Document("root.items[1] = 2", java.util.Map.of("structured_type", "json")),
                new Document("root.items[2] = 3", java.util.Map.of("structured_type", "json"))
        ));
        when(metaService.markDocumentIndexed(any(String.class), any(String.class), any(String.class), eq(2)))
                .thenReturn(indexedMeta);

        service.ingestDocument(file, "kb-1", "user-1", false);

        ArgumentCaptor<List<Document>> chunkCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(chunkCaptor.capture());
        assertEquals(2, chunkCaptor.getValue().size());
        assertEquals("STRUCTURED", chunkCaptor.getValue().get(0).getMetadata().get("chunk_strategy"));
        assertEquals(2, chunkCaptor.getValue().get(0).getMetadata().get("structured_batch_size"));
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

        when(metaService.findLatestByKnowledgeBaseAndFilename("kb-1", "report.xlsx")).thenReturn(null);
        when(metaService.getKnowledgeBase("kb-1")).thenReturn(kb);
        when(excelParser.parse(any(Resource.class))).thenThrow(new BizException("parse failed"));

        BizException exception = assertThrows(BizException.class, () -> service.ingestDocument(file, "kb-1", "user-1", false));

        ArgumentCaptor<DocumentMeta> processingCaptor = ArgumentCaptor.forClass(DocumentMeta.class);
        verify(metaService).createProcessingDocumentMeta(processingCaptor.capture());
        verify(metaService).markDocumentFailed(eq(processingCaptor.getValue().getId()), eq("parse failed"));
        assertEquals("parse failed", exception.getMessage());
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

    @Test
    void reindexDocumentDownloadsSourceAndRebuildsIndex() {
        DocumentMeta doc = DocumentMeta.builder()
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

        when(metaService.prepareDocumentForReindex("doc-1")).thenReturn(doc);
        when(metaService.getKnowledgeBase("kb-1")).thenReturn(kb);
        when(fileStorageService.download("kb-1/doc-1/report.xlsx"))
                .thenReturn(new ByteArrayInputStream("sheet-data".getBytes(StandardCharsets.UTF_8)));
        when(excelParser.parse(any(Resource.class))).thenReturn(List.of(new Document("Quarterly sales data")));
        when(metaService.markDocumentIndexed("doc-1", "kb-1/doc-1/report.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 1)).thenReturn(indexedMeta);

        DocumentMeta result = service.reindexDocument("doc-1");

        verify(fileStorageService).download("kb-1/doc-1/report.xlsx");
        verify(vectorStore).add(any());
        assertEquals(DocumentMeta.STATUS_INDEXED, result.getStatus());
    }
}
