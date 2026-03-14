package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.rag.model.DocumentMeta;
import com.huah.ai.platform.rag.model.KnowledgeBase;
import com.huah.ai.platform.rag.repository.DocumentMetaRepository;
import com.huah.ai.platform.rag.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档元数据 & 知识库管理 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentMetaService {

    private final DocumentMetaRepository docRepo;
    private final KnowledgeBaseRepository kbRepo;

    // ===== 知识库 =====

    public KnowledgeBase getKnowledgeBase(String id) {
        return kbRepo.findById(id)
                .orElseThrow(() -> new BizException("知识库不存在: " + id));
    }

    public List<KnowledgeBase> listKnowledgeBases() {
        return kbRepo.findAll();
    }

    public KnowledgeBase createKnowledgeBase(KnowledgeBase kb) {
        return kbRepo.save(kb);
    }

    public KnowledgeBase updateKnowledgeBase(String id, KnowledgeBase update) {
        KnowledgeBase kb = getKnowledgeBase(id);
        if (update.getName() != null) kb.setName(update.getName());
        if (update.getDescription() != null) kb.setDescription(update.getDescription());
        if (update.getDepartment() != null) kb.setDepartment(update.getDepartment());
        if (update.getChunkSize() > 0) kb.setChunkSize(update.getChunkSize());
        if (update.getChunkOverlap() >= 0) kb.setChunkOverlap(update.getChunkOverlap());
        if (update.getStatus() != null) kb.setStatus(update.getStatus());
        return kbRepo.save(kb);
    }

    public void deleteKnowledgeBase(String id) {
        KnowledgeBase kb = getKnowledgeBase(id);
        List<DocumentMeta> docs = docRepo.findByKnowledgeBaseId(id);
        if (!docs.isEmpty()) {
            throw new BizException("知识库下还有 " + docs.size() + " 个文档，请先删除文档");
        }
        kbRepo.delete(kb);
    }

    // ===== 文档元数据 =====

    public DocumentMeta saveDocumentMeta(DocumentMeta meta) {
        meta.setIndexedAt(LocalDateTime.now());
        meta.setStatus("INDEXED");
        DocumentMeta saved = docRepo.save(meta);
        // 更新知识库统计
        kbRepo.findById(meta.getKnowledgeBaseId()).ifPresent(kb -> {
            kb.setDocumentCount(kb.getDocumentCount() + 1);
            kb.setTotalChunks(kb.getTotalChunks() + meta.getChunkCount());
            kbRepo.save(kb);
        });
        return saved;
    }

    public List<DocumentMeta> listDocuments(String kbId) {
        return docRepo.findByKnowledgeBaseId(kbId);
    }

    public void deleteDocument(String docId) {
        docRepo.deleteById(docId);
    }
}
