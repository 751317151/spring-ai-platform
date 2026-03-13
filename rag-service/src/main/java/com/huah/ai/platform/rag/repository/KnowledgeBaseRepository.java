package com.huah.ai.platform.rag.repository;

import com.huah.ai.platform.rag.model.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, String> {
    List<KnowledgeBase> findByStatus(String status);
    List<KnowledgeBase> findByDepartment(String department);
}
