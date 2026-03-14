package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.rag.mapper.DocumentMetaMapper;
import com.huah.ai.platform.rag.mapper.KnowledgeBaseMapper;
import com.huah.ai.platform.rag.model.DocumentMeta;
import com.huah.ai.platform.rag.model.KnowledgeBase;
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

    private final DocumentMetaMapper docMapper;
    private final KnowledgeBaseMapper kbMapper;

    // ===== 知识库 =====

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
        if (update.getName() != null) kb.setName(update.getName());
        if (update.getDescription() != null) kb.setDescription(update.getDescription());
        if (update.getDepartment() != null) kb.setDepartment(update.getDepartment());
        if (update.getChunkSize() > 0) kb.setChunkSize(update.getChunkSize());
        if (update.getChunkOverlap() >= 0) kb.setChunkOverlap(update.getChunkOverlap());
        if (update.getStatus() != null) kb.setStatus(update.getStatus());
        kbMapper.updateById(kb);
        return kb;
    }

    public void deleteKnowledgeBase(String id) {
        KnowledgeBase kb = getKnowledgeBase(id);
        List<DocumentMeta> docs = docMapper.selectByKnowledgeBaseId(id);
        if (!docs.isEmpty()) {
            throw new BizException("知识库下还有 " + docs.size() + " 个文档，请先删除文档");
        }
        kbMapper.deleteById(kb.getId());
    }

    // ===== 文档元数据 =====

    public DocumentMeta saveDocumentMeta(DocumentMeta meta) {
        meta.setIndexedAt(LocalDateTime.now());
        meta.setStatus("INDEXED");
        docMapper.insert(meta);
        // 更新知识库统计
        KnowledgeBase kb = kbMapper.selectById(meta.getKnowledgeBaseId());
        if (kb != null) {
            kb.setDocumentCount(kb.getDocumentCount() + 1);
            kb.setTotalChunks(kb.getTotalChunks() + meta.getChunkCount());
            kbMapper.updateById(kb);
        }
        return meta;
    }

    public List<DocumentMeta> listDocuments(String kbId) {
        return docMapper.selectByKnowledgeBaseId(kbId);
    }

    public void deleteDocument(String docId) {
        docMapper.deleteById(docId);
    }
}
