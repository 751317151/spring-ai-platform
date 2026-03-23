package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.rag.metrics.RagMetricsService;
import com.huah.ai.platform.rag.model.DocumentMeta;
import com.huah.ai.platform.rag.model.KnowledgeBase;
import com.huah.ai.platform.rag.parser.ExcelDocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文档 ETL 入库服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final DocumentMetaService metaService;
    private final FileStorageService fileStorageService;
    private final ExcelDocumentParser excelParser;
    private final RagMetricsService metricsService;

    public DocumentMeta ingestDocument(MultipartFile file, String knowledgeBaseId, String uploadedBy) {
        String filename = file.getOriginalFilename();
        String docId = UUID.randomUUID().toString();
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String storageKey = knowledgeBaseId + "/" + docId + "/" + filename;
        log.info("开始入库文档: {}, 知识库: {}, docId: {}", filename, knowledgeBaseId, docId);

        KnowledgeBase kb = metaService.getKnowledgeBase(knowledgeBaseId);
        DocumentMeta meta = DocumentMeta.builder()
                .id(docId)
                .filename(filename)
                .knowledgeBaseId(knowledgeBaseId)
                .fileSize(file.getSize())
                .storagePath(storageKey)
                .contentType(contentType)
                .uploadedBy(uploadedBy)
                .status(DocumentMeta.STATUS_PROCESSING)
                .build();
        metaService.createProcessingDocumentMeta(meta);

        boolean uploaded = false;
        try {
            fileStorageService.upload(storageKey, file.getInputStream(), file.getSize(), contentType);
            uploaded = true;
            return ingestContent(docId, filename, knowledgeBaseId, uploadedBy, contentType, storageKey, kb, file.getBytes());
        } catch (BizException e) {
            compensateFailedIngestion(docId, storageKey, e.getMessage(), uploaded);
            throw e;
        } catch (Exception e) {
            metricsService.recordDependencyFailure("vector-store", "add");
            String message = "文档入库失败: " + e.getMessage();
            compensateFailedIngestion(docId, storageKey, message, uploaded);
            throw new BizException(message);
        }
    }

    public DocumentMeta retryDocument(String docId) {
        DocumentMeta meta = metaService.resetFailedDocumentForRetry(docId);
        KnowledgeBase kb = metaService.getKnowledgeBase(meta.getKnowledgeBaseId());

        try (InputStream inputStream = fileStorageService.download(meta.getStoragePath())) {
            byte[] content = StreamUtils.copyToByteArray(inputStream);
            log.info("开始重试失败文档: {}, knowledgeBaseId={}, docId={}", meta.getFilename(), meta.getKnowledgeBaseId(), docId);
            return ingestContent(
                    docId,
                    meta.getFilename(),
                    meta.getKnowledgeBaseId(),
                    meta.getUploadedBy(),
                    meta.getContentType(),
                    meta.getStoragePath(),
                    kb,
                    content
            );
        } catch (BizException e) {
            compensateFailedIngestion(docId, meta.getStoragePath(), e.getMessage(), true);
            throw e;
        } catch (IOException e) {
            String message = "读取待重试文档失败: " + e.getMessage();
            compensateFailedIngestion(docId, meta.getStoragePath(), message, true);
            throw new BizException(message);
        } catch (Exception e) {
            metricsService.recordDependencyFailure("vector-store", "retry");
            String message = "重试文档入库失败: " + e.getMessage();
            compensateFailedIngestion(docId, meta.getStoragePath(), message, true);
            throw new BizException(message);
        }
    }

    private DocumentMeta ingestContent(
            String docId,
            String filename,
            String knowledgeBaseId,
            String uploadedBy,
            String contentType,
            String storageKey,
            KnowledgeBase kb,
            byte[] content
    ) {
        long parseStart = System.nanoTime();
        List<Document> documents = parseDocument(filename, content);
        metricsService.recordStageLatency("document.parse", elapsedMillis(parseStart), true);
        log.info("文档解析完成: {}, 共 {} 段", filename, documents.size());

        documents.forEach(doc -> doc.getMetadata().putAll(Map.of(
                "doc_id", docId,
                "kb_id", knowledgeBaseId,
                "filename", filename,
                "uploaded_by", uploadedBy == null ? "anonymous" : uploadedBy,
                "file_type", getFileExtension(filename)
        )));

        TokenTextSplitter splitter = new TokenTextSplitter(
                kb.getChunkSize(),
                kb.getChunkOverlap(),
                5, 10000, true
        );
        List<Document> chunks = splitter.apply(documents);
        log.info("文档切片完成: {}, 共 {} 个 chunk", filename, chunks.size());

        long vectorizeStart = System.nanoTime();
        vectorStore.add(chunks);
        metricsService.recordStageLatency("vector-store.add", elapsedMillis(vectorizeStart), true);
        log.info("文档向量化完成: {}", filename);

        return metaService.markDocumentIndexed(docId, storageKey, contentType, chunks.size());
    }

    private void compensateFailedIngestion(String docId, String storageKey, String errorMessage, boolean keepSourceFile) {
        if (!keepSourceFile) {
            try {
                fileStorageService.delete(storageKey);
            } catch (Exception cleanupError) {
                log.warn("文档入库失败后的文件清理失败: docId={}, error={}", docId, cleanupError.getMessage());
            }
        }

        try {
            metaService.markDocumentFailed(docId, errorMessage);
        } catch (Exception markError) {
            metricsService.recordDependencyFailure("database", "mark-document-failed");
            log.warn("标记文档失败状态失败: docId={}, error={}", docId, markError.getMessage());
        }
    }

    private List<Document> parseDocument(String filename, byte[] content) {
        String ext = getFileExtension(filename).toLowerCase();
        try {
            Resource resource = new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            return switch (ext) {
                case "pdf" -> parsePdf(resource);
                case "xlsx", "xls" -> excelParser.parse(resource);
                case "doc", "docx", "ppt", "pptx", "html", "htm", "txt", "md" -> parseTika(resource);
                default -> throw new BizException("不支持的文件格式: " + ext);
            };
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            metricsService.recordDependencyFailure("document-parser", ext);
            log.error("文档解析失败: filename={}, ext={}, error={}", filename, ext, e.getMessage(), e);
            throw new BizException("文档解析失败: " + e.getMessage());
        }
    }

    private List<Document> parsePdf(Resource resource) {
        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageTopMargin(0)
                .withPagesPerDocument(1)
                .build();
        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource, config);
        return reader.get();
    }

    private List<Document> parseTika(Resource resource) {
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        return reader.get();
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private long elapsedMillis(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
