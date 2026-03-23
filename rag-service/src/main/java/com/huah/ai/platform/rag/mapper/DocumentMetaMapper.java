package com.huah.ai.platform.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.rag.model.DocumentMeta;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentMetaMapper extends BaseMapper<DocumentMeta> {

    @Select("SELECT * FROM document_meta WHERE knowledge_base_id = #{kbId}")
    List<DocumentMeta> selectByKnowledgeBaseId(@Param("kbId") String knowledgeBaseId);

    @Select("SELECT COUNT(*) FROM document_meta WHERE knowledge_base_id = #{kbId}")
    long countByKnowledgeBaseId(@Param("kbId") String knowledgeBaseId);

    @Select("""
            SELECT * FROM document_meta
            WHERE status = 'FAILED'
              AND storage_path IS NOT NULL
            ORDER BY created_at ASC
            LIMIT #{limit}
            """)
    List<DocumentMeta> selectRetryCandidates(@Param("limit") int limit);
}
