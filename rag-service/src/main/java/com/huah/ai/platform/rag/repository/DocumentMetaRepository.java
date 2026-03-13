package com.huah.ai.platform.rag.repository;

import com.huah.ai.platform.rag.model.DocumentMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentMetaRepository extends JpaRepository<DocumentMeta, String> {
    List<DocumentMeta> findByKnowledgeBaseId(String knowledgeBaseId);
    long countByKnowledgeBaseId(String knowledgeBaseId);
}
