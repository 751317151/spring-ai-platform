package com.huah.ai.platform.common.persistence.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface AiEvidenceFeedbackMapper extends BaseMapper<AiEvidenceFeedbackEntity> {

    AiEvidenceFeedbackEntity selectByResponseIdAndChunkId(
            @Param("responseId") Long responseId,
            @Param("chunkId") String chunkId);

    Map<String, Object> countStatsByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);
}
