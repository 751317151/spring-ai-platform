package com.huah.ai.platform.agent.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AiAuditLogMapper extends BaseMapper<AiAuditLog> {

    List<AiAuditLog> selectByUserIdOrderByCreatedAtDesc(@Param("userId") String userId);

    List<AiAuditLog> selectByAgentTypeAfter(@Param("agentType") String agentType, @Param("after") LocalDateTime after);

    List<Map<String, Object>> statsGroupByAgent(@Param("since") LocalDateTime since);

    List<Map<String, Object>> topTokenUsersSince(@Param("since") LocalDateTime since);

    long countByUserIdAfter(@Param("userId") String userId, @Param("after") LocalDateTime after);

    long countByAgentType(@Param("agentType") String agentType);

    long countByAgentTypeBefore(@Param("agentType") String agentType, @Param("before") LocalDateTime before);

    List<AiAuditLog> selectArchiveCandidates(@Param("agentType") String agentType,
                                             @Param("before") LocalDateTime before,
                                             @Param("limit") int limit);

    List<AiAuditLog> selectArchiveCandidatesBatch(@Param("agentType") String agentType,
                                                  @Param("before") LocalDateTime before,
                                                  @Param("limit") int limit,
                                                  @Param("offset") long offset);

    int deleteByAgentTypeBefore(@Param("agentType") String agentType, @Param("before") LocalDateTime before);
}
