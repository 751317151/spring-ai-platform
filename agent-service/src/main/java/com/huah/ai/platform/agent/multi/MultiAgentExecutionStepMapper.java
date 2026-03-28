package com.huah.ai.platform.agent.multi;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MultiAgentExecutionStepMapper extends BaseMapper<MultiAgentExecutionStep> {

    List<MultiAgentExecutionStep> selectByTraceId(@Param("traceId") String traceId);

    long countByAgentTypeBefore(@Param("agentType") String agentType,
                                @Param("before") LocalDateTime before);

    int deleteByAgentTypeBefore(@Param("agentType") String agentType,
                                @Param("before") LocalDateTime before);
}
