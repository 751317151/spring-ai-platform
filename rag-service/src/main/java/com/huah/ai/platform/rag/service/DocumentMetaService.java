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
