package com.huah.ai.platform.agent.multi;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MultiAgentExecutionStepMapper extends BaseMapper<MultiAgentExecutionStep> {

    @Select("""
            SELECT * FROM ai_multi_agent_trace_steps
            WHERE trace_id = #{traceId}
            ORDER BY step_order ASC, created_at ASC
            """)
    List<MultiAgentExecutionStep> selectByTraceId(@Param("traceId") String traceId);

    @Select("""
            SELECT COUNT(*)
            FROM ai_multi_agent_trace_steps s
            INNER JOIN ai_multi_agent_traces t ON t.trace_id = s.trace_id
            WHERE t.agent_type = #{agentType}
              AND s.created_at < #{before}
            """)
    long countByAgentTypeBefore(@Param("agentType") String agentType,
                                @Param("before") LocalDateTime before);

    @Delete("""
            DELETE FROM ai_multi_agent_trace_steps
            WHERE trace_id IN (
                SELECT trace_id FROM ai_multi_agent_traces
                WHERE agent_type = #{agentType}
                  AND created_at < #{before}
            )
            """)
    int deleteByAgentTypeBefore(@Param("agentType") String agentType,
                                @Param("before") LocalDateTime before);
}
