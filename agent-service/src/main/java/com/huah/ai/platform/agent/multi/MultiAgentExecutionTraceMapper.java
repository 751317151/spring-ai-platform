package com.huah.ai.platform.agent.multi;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MultiAgentExecutionTraceMapper extends BaseMapper<MultiAgentExecutionTrace> {

    @Select("""
            SELECT * FROM ai_multi_agent_traces
            WHERE trace_id = #{traceId}
              AND user_id = #{userId}
            LIMIT 1
            """)
    MultiAgentExecutionTrace selectByTraceIdAndUserId(@Param("traceId") String traceId,
                                                      @Param("userId") String userId);

    @Select("""
            <script>
            SELECT * FROM ai_multi_agent_traces
            WHERE user_id = #{userId}
            <if test="sessionId != null and sessionId != ''">
              AND session_id = #{sessionId}
            </if>
            ORDER BY created_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<MultiAgentExecutionTrace> selectRecentByUser(@Param("userId") String userId,
                                                      @Param("sessionId") String sessionId,
                                                      @Param("limit") int limit);

    @Select("""
            <script>
            SELECT * FROM ai_multi_agent_traces
            WHERE created_at &gt;= #{after}
            ORDER BY created_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<MultiAgentExecutionTrace> selectRecentAfter(@Param("after") LocalDateTime after,
                                                     @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM ai_multi_agent_traces WHERE agent_type = #{agentType}")
    long countByAgentType(@Param("agentType") String agentType);

    @Select("SELECT COUNT(*) FROM ai_multi_agent_traces WHERE agent_type = #{agentType} AND created_at < #{before}")
    long countByAgentTypeBefore(@Param("agentType") String agentType,
                                @Param("before") LocalDateTime before);

    @Select("""
            SELECT * FROM ai_multi_agent_traces
            WHERE agent_type = #{agentType}
              AND created_at < #{before}
            ORDER BY created_at ASC
            LIMIT #{limit}
            """)
    List<MultiAgentExecutionTrace> selectArchiveCandidates(@Param("agentType") String agentType,
                                                           @Param("before") LocalDateTime before,
                                                           @Param("limit") int limit);

    @Select("""
            SELECT * FROM ai_multi_agent_traces
            WHERE agent_type = #{agentType}
              AND created_at < #{before}
            ORDER BY created_at ASC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<MultiAgentExecutionTrace> selectArchiveCandidatesBatch(@Param("agentType") String agentType,
                                                                @Param("before") LocalDateTime before,
                                                                @Param("limit") int limit,
                                                                @Param("offset") long offset);

    @org.apache.ibatis.annotations.Delete("DELETE FROM ai_multi_agent_traces WHERE agent_type = #{agentType} AND created_at < #{before}")
    int deleteByAgentTypeBefore(@Param("agentType") String agentType,
                                @Param("before") LocalDateTime before);
}
