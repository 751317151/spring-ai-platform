package com.huah.ai.platform.rag.mapper;

import com.huah.ai.platform.rag.model.VectorStoreChunkEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VectorStoreSearchMapper {

    List<VectorStoreChunkEntity> searchByKeywords(
            @Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("terms") List<String> terms,
            @Param("limit") int limit);
}
