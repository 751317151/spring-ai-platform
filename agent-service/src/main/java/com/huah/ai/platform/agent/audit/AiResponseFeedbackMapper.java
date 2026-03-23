package com.huah.ai.platform.agent.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiResponseFeedbackMapper extends BaseMapper<AiResponseFeedback> {

    @Select("SELECT * FROM ai_response_feedback WHERE response_id = #{responseId}")
    AiResponseFeedback selectByResponseId(@Param("responseId") String responseId);
}
