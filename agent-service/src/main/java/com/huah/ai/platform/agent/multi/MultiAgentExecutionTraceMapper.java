package com.huah.ai.platform.agent.multi;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MultiAgentExecutionTraceMapper extends BaseMapper<MultiAgentExecutionTrace> {

    MultiAgentExecutionTrace selectByTraceIdAndUserId(@Param("traceId") String traceId,
                                                      @Param("userId") String userId);

    List<MultiAgentExecutionTrace> selectRecentByUser(@Param("userId") String userId,
                                                      @Param("sessionId") String sessionId,
                                                      @Param("limit") int limit);

    List<MultiAgentExecutionTrace> selectRecentAfter(@Param("after") LocalDateTime after,
                                                     @Param("limit") int limit);

    long countByAgentType(@Param("agentType") String agentType);

    long countByAgentTypeBefore(@Param("agentType") String agentType,
                                @Param("before") LocalDateTime before);

    List<MultiAgentExecutionTrace> selectArchiveCandidates(@Param("agentType") String agentType,
                                                           @Param("before") LocalDateTime before,
                                                           @Param("limit") int limit);

    List<MultiAgentExecutionTrace> selectArchiveCandidatesBatch(@Param("agentType") String agentType,
                                                                @Param("before") LocalDateTime before,
                                                                @Param("limit") int limit,
                                                                @Param("offset") long offset);

    int deleteByAgentTypeBefore(@Param("agentType") String agentType,
                                @Param("before") LocalDateTime before);
}
