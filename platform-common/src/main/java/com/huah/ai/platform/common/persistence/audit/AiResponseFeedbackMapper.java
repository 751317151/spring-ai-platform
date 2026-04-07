package com.huah.ai.platform.common.persistence.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface AiResponseFeedbackMapper extends BaseMapper<AiResponseFeedbackEntity> {

    AiResponseFeedbackEntity selectByResponseId(@Param("responseId") Long responseId);

    Map<String, Object> countStatsBySourceTypeAndKnowledgeBaseId(
            @Param("sourceType") String sourceType,
            @Param("knowledgeBaseId") Long knowledgeBaseId);
}
