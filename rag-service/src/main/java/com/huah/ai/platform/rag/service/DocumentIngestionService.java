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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private static final int CHUNK_PREVIEW_LENGTH = 160;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pdf", "xlsx", "xls", "doc", "docx", "ppt", "pptx", "html", "htm", "txt", "md"
    );
    private static final Set<String> GRACEFUL_UNSUPPORTED_EXTENSIONS = Set.of(
            "csv", "json", "xml", "mol", "sdf", "cdx"
    );

    private final VectorStore vectorStore;
    private final DocumentMetaService metaService;
    private final FileStorageService fileStorageService;
    private final ExcelDocumentParser excelParser;
    private final RagMetricsService metricsService;

    public DocumentMeta ingestDocument(MultipartFile file, String knowledgeBaseId, String uploadedBy) {
        return ingestDocument(file, knowledgeBaseId, uploadedBy, false);
    }

    public DocumentMeta ingestDocument(MultipartFile file, String knowledgeBaseId, String uploadedBy, boolean replaceExisting) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new BizException("Filename cannot be empty.");
        }

        DocumentMeta duplicate = metaService.findLatestByKnowledgeBaseAndFilename(knowledgeBaseId, filename);
        if (duplicate != null) {
            if (!replaceExisting) {
                throw new BizException("A document with the same filename already exists in this knowledge base. Enable replacement or delete it first.");
            }
            metaService.deleteDocument(duplicate.getId());
        }

        String docId = UUID.randomUUID().toString();
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String storageKey = knowledgeBaseId + "/" + docId + "/" + filename;
        String extension = getFileExtension(filename).toLowerCase();
        log.info("Start ingesting document: {}, knowledgeBaseId={}, docId={}", filename, knowledgeBaseId, docId);

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

            if (GRACEFUL_UNSUPPORTED_EXTENSIONS.contains(extension)) {
                return metaService.markDocumentFailed(docId, unsupportedStructuredFileMessage(extension));
            }
            if (!SUPPORTED_EXTENSIONS.contains(extension)) {
                throw new BizException("Unsupported file type: " + extension);
            }

            return ingestContent(docId, filename, knowledgeBaseId, uploadedBy, contentType, storageKey, kb, file.getBytes());
        } catch (BizException e) {
            compensateFailedIngestion(docId, storageKey, e.getMessage(), uploaded);
            throw e;
        } catch (Exception e) {
            metricsService.recordDependencyFailure("vector-store", "add");
            String message = "Document ingestion failed: " + e.getMessage();
            compensateFailedIngestion(docId, storageKey, message, uploaded);
            throw new BizException(message);
        }
    }

    public DocumentMeta retryDocument(String docId) {
        DocumentMeta meta = metaService.resetFailedDocumentForRetry(docId);
        KnowledgeBase kb = metaService.getKnowledgeBase(meta.getKnowledgeBaseId());
        String extension = getFileExtension(meta.getFilename()).toLowerCase();

        try (InputStream inputStream = fileStorageService.download(meta.getStoragePath())) {
            if (GRACEFUL_UNSUPPORTED_EXTENSIONS.contains(extension)) {
                return metaService.markDocumentFailed(docId, unsupportedStructuredFileMessage(extension));
            }

            byte[] content = StreamUtils.copyToByteArray(inputStream);
            log.info("Retry failed document: {}, knowledgeBaseId={}, docId={}", meta.getFilename(), meta.getKnowledgeBaseId(), docId);
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
            String message = "Failed to read retry source file: " + e.getMessage();
            compensateFailedIngestion(docId, meta.getStoragePath(), message, true);
            throw new BizException(message);
        } catch (Exception e) {
            metricsService.recordDependencyFailure("vector-store", "retry");
            String message = "Retry document ingestion failed: " + e.getMessage();
            compensateFailedIngestion(docId, meta.getStoragePath(), message, true);
            throw new BizException(message);
        }
    }

    public DocumentMeta reindexDocument(String docId) {
        DocumentMeta meta = metaService.prepareDocumentForReindex(docId);
        KnowledgeBase kb = metaService.getKnowledgeBase(meta.getKnowledgeBaseId());
        String extension = getFileExtension(meta.getFilename()).toLowerCase();

        try (InputStream inputStream = fileStorageService.download(meta.getStoragePath())) {
            if (GRACEFUL_UNSUPPORTED_EXTENSIONS.contains(extension)) {
                return metaService.markDocumentFailed(docId, unsupportedStructuredFileMessage(extension));
            }

            byte[] content = StreamUtils.copyToByteArray(inputStream);
            log.info("Reindex document: {}, knowledgeBaseId={}, docId={}", meta.getFilename(), meta.getKnowledgeBaseId(), docId);
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
            String message = "Failed to read source file for reindex: " + e.getMessage();
            compensateFailedIngestion(docId, meta.getStoragePath(), message, true);
            throw new BizException(message);
        } catch (Exception e) {
            metricsService.recordDependencyFailure("vector-store", "reindex");
            String message = "Reindex document failed: " + e.getMessage();
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
        log.info("Document parsed: {}, segments={}", filename, documents.size());

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
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            String text = chunk.getText() == null ? "" : chunk.getText();
            chunk.getMetadata().put("chunk_index", i + 1);
            chunk.getMetadata().put("chunk_preview", buildChunkPreview(text));
            chunk.getMetadata().put("char_count", text.length());
        }
        log.info("Document chunked: {}, chunks={}", filename, chunks.size());

        long vectorizeStart = System.nanoTime();
        vectorStore.add(chunks);
        metricsService.recordStageLatency("vector-store.add", elapsedMillis(vectorizeStart), true);
        log.info("Document vectorized: {}", filename);

        return metaService.markDocumentIndexed(docId, storageKey, contentType, chunks.size());
    }

    private void compensateFailedIngestion(String docId, String storageKey, String errorMessage, boolean keepSourceFile) {
        if (!keepSourceFile) {
            try {
                fileStorageService.delete(storageKey);
            } catch (Exception cleanupError) {
                log.warn("Failed to clean uploaded file after ingestion error: docId={}, error={}", docId, cleanupError.getMessage());
            }
        }

        try {
            metaService.markDocumentFailed(docId, errorMessage);
        } catch (Exception markError) {
            metricsService.recordDependencyFailure("database", "mark-document-failed");
            log.warn("Failed to mark document as failed: docId={}, error={}", docId, markError.getMessage());
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
                default -> throw new BizException("Unsupported file type: " + ext);
            };
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            metricsService.recordDependencyFailure("document-parser", ext);
            log.error("Document parsing failed: filename={}, ext={}, error={}", filename, ext, e.getMessage(), e);
            throw new BizException("Document parsing failed: " + e.getMessage());
        }
    }

    private List<Document> parsePdf(Resource resource) {
        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageTopMargin(0)
                .withPagesPerDocument(1)
                .build();
        return new PagePdfDocumentReader(resource, config).get();
    }

    private List<Document> parseTika(Resource resource) {
        return new TikaDocumentReader(resource).get();
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

    private String buildChunkPreview(String text) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= CHUNK_PREVIEW_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, CHUNK_PREVIEW_LENGTH) + "...";
    }

    private String unsupportedStructuredFileMessage(String extension) {
        return "Structured file type ." + extension + " is stored successfully, but parsing is not supported yet. You can keep the source file and reprocess it after adding a parser.";
    }
}
