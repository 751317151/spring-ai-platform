package com.huah.ai.platform.agent.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiToolAuditLogMapper extends BaseMapper<AiToolAuditLog> {

    List<AiToolAuditLog> selectRecent(@Param("userId") String userId,
                                      @Param("agentType") String agentType,
                                      @Param("toolName") String toolName,
                                      @Param("traceId") String traceId,
                                      @Param("limit") int limit);

    List<AiToolAuditLog> selectRecentByAgentTypeAfter(@Param("agentType") String agentType,
                                                      @Param("after") LocalDateTime after,
                                                      @Param("limit") int limit);

    long countByAgentType(@Param("agentType") String agentType);

    long countByAgentTypeBefore(@Param("agentType") String agentType,
                                @Param("before") LocalDateTime before);

    List<AiToolAuditLog> selectArchiveCandidates(@Param("agentType") String agentType,
                                                 @Param("before") LocalDateTime before,
                                                 @Param("limit") int limit);

    List<AiToolAuditLog> selectArchiveCandidatesBatch(@Param("agentType") String agentType,
                                                      @Param("before") LocalDateTime before,
                                                      @Param("limit") int limit,
                                                      @Param("offset") long offset);

    int deleteByAgentTypeBefore(@Param("agentType") String agentType,
                                @Param("before") LocalDateTime before);
}
