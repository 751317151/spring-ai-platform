package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.rag.mapper.DocumentMetaMapper;
import com.huah.ai.platform.rag.mapper.KnowledgeBaseMapper;
import com.huah.ai.platform.rag.metrics.RagMetricsService;
import com.huah.ai.platform.rag.model.DocumentMeta;
import com.huah.ai.platform.rag.model.KnowledgeBase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档元数据和知识库管理服务。
 */
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
            throw new BizException("知识库不存在: " + id);
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
            throw new BizException("知识库下还有 " + docs.size() + " 个文档，请先删除文档。");
        }
        kbMapper.deleteById(kb.getId());
    }

    public DocumentMeta getDocument(String docId) {
        DocumentMeta doc = docMapper.selectById(docId);
        if (doc == null) {
            throw new BizException("文档不存在: " + docId);
        }
        return doc;
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
            throw new BizException("只有失败状态的文档才允许重试: " + docId);
        }
        if (meta.getStoragePath() == null || meta.getStoragePath().isBlank()) {
            throw new BizException("文档缺少原始文件存储路径，无法重试: " + docId);
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

    public void deleteDocument(String docId) {
        DocumentMeta doc = getDocument(docId);

        if (doc.getStoragePath() != null) {
            fileStorageService.delete(doc.getStoragePath());
        }

        try {
            jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'doc_id' = ?", docId);
            log.info("向量数据删除完成: docId={}", docId);
        } catch (Exception e) {
            metricsService.recordDependencyFailure("database", "delete-vectors");
            log.error("向量数据删除失败: docId={}, error={}", docId, e.getMessage());
        }

        KnowledgeBase kb = kbMapper.selectById(doc.getKnowledgeBaseId());
        if (kb != null && DocumentMeta.STATUS_INDEXED.equals(doc.getStatus())) {
            kb.setDocumentCount(Math.max(0, kb.getDocumentCount() - 1));
            kb.setTotalChunks(Math.max(0, kb.getTotalChunks() - doc.getChunkCount()));
            kbMapper.updateById(kb);
        }

        docMapper.deleteById(docId);
        log.info("文档删除完成: id={}, file={}", docId, doc.getFilename());
    }
}
