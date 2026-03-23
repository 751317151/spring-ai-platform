package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.rag.mapper.DocumentMetaMapper;
import com.huah.ai.platform.rag.mapper.KnowledgeBaseMapper;
import com.huah.ai.platform.rag.metrics.RagMetricsService;
import com.huah.ai.platform.rag.model.DocumentChunkPreview;
import com.huah.ai.platform.rag.model.DocumentMeta;
import com.huah.ai.platform.rag.model.KnowledgeBase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentMetaService {

    private final DocumentMetaMapper docMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final FileStorageService fileStorageService;
    private final JdbcTemplate jdbcTemplate;
    private final RagMetricsService metricsService;

    public KnowledgeBase getKnowledgeBase(String id) {
        KnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new BizException("Knowledge base not found: " + id);
        }
        return kb;
    }

    public List<KnowledgeBase> listKnowledgeBases() {
        return kbMapper.selectList(null);
    }

    public KnowledgeBase createKnowledgeBase(KnowledgeBase kb) {
        kbMapper.insert(kb);
        return kb;
    }

    public KnowledgeBase updateKnowledgeBase(String id, KnowledgeBase update) {
        KnowledgeBase kb = getKnowledgeBase(id);
        if (update.getName() != null) {
            kb.setName(update.getName());
        }
        if (update.getDescription() != null) {
            kb.setDescription(update.getDescription());
        }
        if (update.getDepartment() != null) {
            kb.setDepartment(update.getDepartment());
        }
        if (update.getChunkSize() > 0) {
            kb.setChunkSize(update.getChunkSize());
        }
        if (update.getChunkOverlap() >= 0) {
            kb.setChunkOverlap(update.getChunkOverlap());
        }
        if (update.getStatus() != null) {
            kb.setStatus(update.getStatus());
        }
        kbMapper.updateById(kb);
        return kb;
    }

    public void deleteKnowledgeBase(String id) {
        KnowledgeBase kb = getKnowledgeBase(id);
        List<DocumentMeta> docs = docMapper.selectByKnowledgeBaseId(id);
        if (!docs.isEmpty()) {
            throw new BizException("Knowledge base still contains " + docs.size() + " documents. Delete them first.");
        }
        kbMapper.deleteById(kb.getId());
    }

    public DocumentMeta getDocument(String docId) {
        DocumentMeta doc = docMapper.selectById(docId);
        if (doc == null) {
            throw new BizException("Document not found: " + docId);
        }
        return doc;
    }

    public DocumentMeta findLatestByKnowledgeBaseAndFilename(String knowledgeBaseId, String filename) {
        return docMapper.selectLatestByKnowledgeBaseIdAndFilename(knowledgeBaseId, filename);
    }

    public DocumentMeta createProcessingDocumentMeta(DocumentMeta meta) {
        meta.setStatus(DocumentMeta.STATUS_PROCESSING);
        meta.setErrorMessage(null);
        meta.setIndexedAt(null);
        docMapper.insert(meta);
        return meta;
    }

    public DocumentMeta resetFailedDocumentForRetry(String docId) {
        DocumentMeta meta = getDocument(docId);
        if (!DocumentMeta.STATUS_FAILED.equals(meta.getStatus())) {
            throw new BizException("Only failed documents can be retried: " + docId);
        }
        ensureSourceFileExists(meta, "retry");
        meta.setStatus(DocumentMeta.STATUS_PROCESSING);
        meta.setErrorMessage(null);
        meta.setIndexedAt(null);
        meta.setChunkCount(0);
        docMapper.updateById(meta);
        return meta;
    }

    public DocumentMeta prepareDocumentForReindex(String docId) {
        DocumentMeta meta = getDocument(docId);
        ensureSourceFileExists(meta, "reindex");

        if (DocumentMeta.STATUS_INDEXED.equals(meta.getStatus())) {
            deleteDocumentVectors(docId, "delete-vectors-for-reindex");

            KnowledgeBase kb = kbMapper.selectById(meta.getKnowledgeBaseId());
            if (kb != null) {
                kb.setDocumentCount(Math.max(0, kb.getDocumentCount() - 1));
                kb.setTotalChunks(Math.max(0, kb.getTotalChunks() - meta.getChunkCount()));
                kbMapper.updateById(kb);
            }
        }

        meta.setStatus(DocumentMeta.STATUS_PROCESSING);
        meta.setErrorMessage(null);
        meta.setIndexedAt(null);
        meta.setChunkCount(0);
        docMapper.updateById(meta);
        return meta;
    }

    public DocumentMeta markDocumentIndexed(String docId, String storagePath, String contentType, int chunkCount) {
        DocumentMeta meta = getDocument(docId);
        boolean newlyIndexed = !DocumentMeta.STATUS_INDEXED.equals(meta.getStatus());

        meta.setStoragePath(storagePath);
        meta.setContentType(contentType);
        meta.setChunkCount(chunkCount);
        meta.setStatus(DocumentMeta.STATUS_INDEXED);
        meta.setErrorMessage(null);
        meta.setIndexedAt(LocalDateTime.now());
        docMapper.updateById(meta);

        if (newlyIndexed) {
            KnowledgeBase kb = kbMapper.selectById(meta.getKnowledgeBaseId());
            if (kb != null) {
                kb.setDocumentCount(kb.getDocumentCount() + 1);
                kb.setTotalChunks(kb.getTotalChunks() + chunkCount);
                kbMapper.updateById(kb);
            }
        }
        return meta;
    }

    public DocumentMeta markDocumentFailed(String docId, String errorMessage) {
        DocumentMeta meta = getDocument(docId);
        meta.setStatus(DocumentMeta.STATUS_FAILED);
        meta.setErrorMessage(errorMessage);
        meta.setIndexedAt(null);
        docMapper.updateById(meta);
        return meta;
    }

    public List<DocumentMeta> listDocuments(String kbId) {
        return docMapper.selectByKnowledgeBaseId(kbId);
    }

    public List<DocumentMeta> listRetryableFailedDocuments(int limit) {
        return docMapper.selectRetryCandidates(limit);
    }

    public List<DocumentChunkPreview> listDocumentChunks(String docId) {
        getDocument(docId);
        return jdbcTemplate.query("""
                        SELECT
                            id::text AS id,
                            content,
                            COALESCE((metadata->>'chunk_index')::int, 0) AS chunk_index,
                            metadata->>'chunk_preview' AS chunk_preview,
                            COALESCE((metadata->>'char_count')::int, char_length(content)) AS char_count
                        FROM vector_store
                        WHERE metadata->>'doc_id' = ?
                        ORDER BY COALESCE((metadata->>'chunk_index')::int, 0), id
                        """,
                (rs, rowNum) -> DocumentChunkPreview.builder()
                        .id(rs.getString("id"))
                        .chunkIndex(rs.getInt("chunk_index"))
                        .content(rs.getString("content"))
                        .preview(rs.getString("chunk_preview"))
                        .charCount(rs.getInt("char_count"))
                        .build(),
                docId
        );
    }

    public void deleteDocument(String docId) {
        DocumentMeta doc = getDocument(docId);

        if (doc.getStoragePath() != null) {
            fileStorageService.delete(doc.getStoragePath());
        }

        deleteDocumentVectors(docId, "delete-vectors");

        KnowledgeBase kb = kbMapper.selectById(doc.getKnowledgeBaseId());
        if (kb != null && DocumentMeta.STATUS_INDEXED.equals(doc.getStatus())) {
            kb.setDocumentCount(Math.max(0, kb.getDocumentCount() - 1));
            kb.setTotalChunks(Math.max(0, kb.getTotalChunks() - doc.getChunkCount()));
            kbMapper.updateById(kb);
        }

        docMapper.deleteById(docId);
        log.info("Document deleted: id={}, file={}", docId, doc.getFilename());
    }

    private void ensureSourceFileExists(DocumentMeta meta, String action) {
        if (meta.getStoragePath() == null || meta.getStoragePath().isBlank()) {
            throw new BizException("Document source file is missing and cannot " + action + ": " + meta.getId());
        }
    }

    private void deleteDocumentVectors(String docId, String metricAction) {
        try {
            jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'doc_id' = ?", docId);
            log.info("Document vectors deleted: docId={}", docId);
        } catch (Exception e) {
            metricsService.recordDependencyFailure("database", metricAction);
            log.error("Document vector deletion failed: docId={}, error={}", docId, e.getMessage());
            throw new BizException("Failed to clean document vectors: " + e.getMessage());
        }
    }
}
