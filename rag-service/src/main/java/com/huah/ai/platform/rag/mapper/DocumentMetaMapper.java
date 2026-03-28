package com.huah.ai.platform.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.rag.model.DocumentMeta;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentMetaMapper extends BaseMapper<DocumentMeta> {

    List<DocumentMeta> selectByKnowledgeBaseId(@Param("kbId") Long knowledgeBaseId);

    long countByKnowledgeBaseId(@Param("kbId") Long knowledgeBaseId);

    List<DocumentMeta> selectRetryCandidates(@Param("limit") int limit);

    DocumentMeta selectLatestByKnowledgeBaseIdAndFilename(@Param("kbId") Long knowledgeBaseId,
                                                          @Param("filename") String filename);
}
