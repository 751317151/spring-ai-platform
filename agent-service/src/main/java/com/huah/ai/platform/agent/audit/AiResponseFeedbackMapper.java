package com.huah.ai.platform.agent.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiResponseFeedbackMapper extends BaseMapper<AiResponseFeedback> {

    AiResponseFeedback selectByResponseId(@Param("responseId") Long responseId);
}
