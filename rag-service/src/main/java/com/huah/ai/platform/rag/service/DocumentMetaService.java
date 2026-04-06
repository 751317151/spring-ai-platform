package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.common.exception.PermissionDeniedException;
import com.huah.ai.platform.rag.mapper.DocumentMetaMapper;
import com.huah.ai.platform.rag.mapper.KnowledgeBaseMapper;
import com.huah.ai.platform.rag.metrics.RagMetricsService;
import com.huah.ai.platform.rag.model.DocumentChunkPreview;
import com.huah.ai.platform.rag.model.DocumentMetaEntity;
import com.huah.ai.platform.rag.model.KnowledgeBaseEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentMetaService {

    private final DocumentMetaMapper docMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final FileStorageService fileStorageService;
    private final JdbcTemplate jdbcTemplate;
    private final RagMetricsService metricsService;

    public KnowledgeBaseEntity getKnowledgeBase(Long id) {
        KnowledgeBaseEntity kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new BizException("Knowledge base not found: " + id);
        }
        return kb;
    }

    public KnowledgeBaseEntity getKnowledgeBase(Long id, AccessContext context) {
        KnowledgeBaseEntity kb = getKnowledgeBase(id);
        ensureKnowledgeBaseAccessible(kb, context);
        return kb;
    }

    public List<KnowledgeBaseEntity> listKnowledgeBases(AccessContext context) {
        return kbMapper.selectList(null).stream()
                .filter(kb -> isKnowledgeBaseAccessible(kb, context))
                .toList();
    }

    public KnowledgeBaseEntity createKnowledgeBase(KnowledgeBaseEntity kb) {
        kbMapper.insert(kb);
        return kb;
    }

    public KnowledgeBaseEntity updateKnowledgeBase(Long id, KnowledgeBaseEntity update) {
        KnowledgeBaseEntity kb = getKnowledgeBase(id);
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
        if (update.getChunkStrategy() != null && !update.getChunkStrategy().isBlank()) {
            kb.setChunkStrategy(update.getChunkStrategy().trim().toUpperCase(Locale.ROOT));
        }
        if (update.getStructuredBatchSize() > 0) {
            kb.setStructuredBatchSize(update.getStructuredBatchSize());
        }
        if (update.getStatus() != null) {
            kb.setStatus(update.getStatus());
        }
        if (update.getVisibilityScope() != null) {
            kb.setVisibilityScope(update.getVisibilityScope());
        }
        kbMapper.updateById(kb);
        return kb;
    }

    public void deleteKnowledgeBase(Long id) {
        KnowledgeBaseEntity kb = getKnowledgeBase(id);
        List<DocumentMetaEntity> docs = docMapper.selectByKnowledgeBaseId(id);
        if (!docs.isEmpty()) {
            throw new BizException("Knowledge base still contains " + docs.size() + " documents. Delete them first.");
        }
        kbMapper.deleteById(kb.getId());
    }

    public DocumentMetaEntity getDocument(Long docId) {
        DocumentMetaEntity doc = docMapper.selectById(docId);
        if (doc == null) {
            throw new BizException("Document not found: " + docId);
        }
        return doc;
    }

    public DocumentMetaEntity findLatestByKnowledgeBaseAndFilename(Long knowledgeBaseId, String filename) {
        return docMapper.selectLatestByKnowledgeBaseIdAndFilename(knowledgeBaseId, filename);
    }

    public DocumentMetaEntity createProcessingDocumentMeta(DocumentMetaEntity meta) {
        meta.setStatus(DocumentMetaEntity.STATUS_PROCESSING);
        meta.setErrorMessage(null);
        meta.setIndexedAt(null);
        docMapper.insert(meta);
        return meta;
    }

    public DocumentMetaEntity resetFailedDocumentForRetry(Long docId) {
        DocumentMetaEntity meta = getDocument(docId);
        if (!DocumentMetaEntity.STATUS_FAILED.equals(meta.getStatus())) {
            throw new BizException("Only failed documents can be retried: " + docId);
        }
        ensureSourceFileExists(meta, "retry");
        meta.setStatus(DocumentMetaEntity.STATUS_PROCESSING);
        meta.setErrorMessage(null);
        meta.setIndexedAt(null);
        meta.setChunkCount(0);
        docMapper.updateById(meta);
        return meta;
    }

    public DocumentMetaEntity prepareDocumentForReindex(Long docId) {
        DocumentMetaEntity meta = getDocument(docId);
        ensureSourceFileExists(meta, "reindex");

        if (DocumentMetaEntity.STATUS_INDEXED.equals(meta.getStatus())) {
            deleteDocumentVectors(docId, "delete-vectors-for-reindex");

            KnowledgeBaseEntity kb = kbMapper.selectById(meta.getKnowledgeBaseId());
            if (kb != null) {
                kb.setDocumentCount(Math.max(0, kb.getDocumentCount() - 1));
                kb.setTotalChunks(Math.max(0, kb.getTotalChunks() - meta.getChunkCount()));
                kbMapper.updateById(kb);
            }
        }

        meta.setStatus(DocumentMetaEntity.STATUS_PROCESSING);
        meta.setErrorMessage(null);
        meta.setIndexedAt(null);
        meta.setChunkCount(0);
        docMapper.updateById(meta);
        return meta;
    }

    public DocumentMetaEntity markDocumentIndexed(Long docId, String storagePath, String contentType, int chunkCount) {
        DocumentMetaEntity meta = getDocument(docId);
        boolean newlyIndexed = !DocumentMetaEntity.STATUS_INDEXED.equals(meta.getStatus());

        meta.setStoragePath(storagePath);
        meta.setContentType(contentType);
        meta.setChunkCount(chunkCount);
        meta.setStatus(DocumentMetaEntity.STATUS_INDEXED);
        meta.setErrorMessage(null);
        meta.setIndexedAt(LocalDateTime.now());
        docMapper.updateById(meta);

        if (newlyIndexed) {
            KnowledgeBaseEntity kb = kbMapper.selectById(meta.getKnowledgeBaseId());
            if (kb != null) {
                kb.setDocumentCount(kb.getDocumentCount() + 1);
                kb.setTotalChunks(kb.getTotalChunks() + chunkCount);
                kbMapper.updateById(kb);
            }
        }
        return meta;
    }

    public DocumentMetaEntity markDocumentFailed(Long docId, String errorMessage) {
        DocumentMetaEntity meta = getDocument(docId);
        meta.setStatus(DocumentMetaEntity.STATUS_FAILED);
        meta.setErrorMessage(errorMessage);
        meta.setIndexedAt(null);
        docMapper.updateById(meta);
        return meta;
    }

    public List<DocumentMetaEntity> listDocuments(Long kbId, AccessContext context) {
        getKnowledgeBase(kbId, context);
        return docMapper.selectByKnowledgeBaseId(kbId);
    }

    public DocumentMetaEntity getDocument(Long docId, AccessContext context) {
        DocumentMetaEntity doc = getDocument(docId);
        getKnowledgeBase(doc.getKnowledgeBaseId(), context);
        return doc;
    }

    public List<DocumentMetaEntity> listRetryableFailedDocuments(int limit) {
        return docMapper.selectRetryCandidates(limit);
    }

    public List<DocumentChunkPreview> listDocumentChunks(Long docId) {
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
                String.valueOf(docId)
        );
    }

    public void deleteDocument(Long docId) {
        DocumentMetaEntity doc = getDocument(docId);

        if (doc.getStoragePath() != null) {
            fileStorageService.delete(doc.getStoragePath());
        }

        deleteDocumentVectors(docId, "delete-vectors");

        KnowledgeBaseEntity kb = kbMapper.selectById(doc.getKnowledgeBaseId());
        if (kb != null && DocumentMetaEntity.STATUS_INDEXED.equals(doc.getStatus())) {
            kb.setDocumentCount(Math.max(0, kb.getDocumentCount() - 1));
            kb.setTotalChunks(Math.max(0, kb.getTotalChunks() - doc.getChunkCount()));
            kbMapper.updateById(kb);
        }

        docMapper.deleteById(docId);
        log.info("Document deleted: id={}, file={}", docId, doc.getFilename());
    }

    private void ensureSourceFileExists(DocumentMetaEntity meta, String action) {
        if (meta.getStoragePath() == null || meta.getStoragePath().isBlank()) {
            throw new BizException("Document source file is missing and cannot " + action + ": " + meta.getId());
        }
    }

    private void deleteDocumentVectors(Long docId, String metricAction) {
        try {
            jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'doc_id' = ?", String.valueOf(docId));
            log.info("Document vectors deleted: docId={}", docId);
        } catch (Exception e) {
            metricsService.recordDependencyFailure("database", metricAction);
            log.error("Document vector deletion failed: docId={}, error={}", docId, e.getMessage());
            throw new BizException("Failed to clean document vectors: " + e.getMessage());
        }
    }

    public void ensureKnowledgeBaseAccessible(Long knowledgeBaseId, AccessContext context) {
        getKnowledgeBase(knowledgeBaseId, context);
    }

    private void ensureKnowledgeBaseAccessible(KnowledgeBaseEntity kb, AccessContext context) {
        if (!isKnowledgeBaseAccessible(kb, context)) {
            throw new PermissionDeniedException("无权限访问该知识库");
        }
    }

    private boolean isKnowledgeBaseAccessible(KnowledgeBaseEntity kb, AccessContext context) {
        if (context == null || context.admin()) {
            return true;
        }
        String visibilityScope = normalize(kb.getVisibilityScope());
        String department = normalize(kb.getDepartment());
        String createdBy = normalize(kb.getCreatedBy());
        String requestDepartment = normalize(context.department());
        String userId = normalize(context.userId());

        if ("public".equals(visibilityScope) || department.isBlank()) {
            return true;
        }
        if ("private".equals(visibilityScope)) {
            return !createdBy.isBlank() && createdBy.equals(userId);
        }
        if (!createdBy.isBlank() && createdBy.equals(userId)) {
            return true;
        }
        return department.equals(requestDepartment);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record AccessContext(String userId, String department, boolean admin) {
    }
}
