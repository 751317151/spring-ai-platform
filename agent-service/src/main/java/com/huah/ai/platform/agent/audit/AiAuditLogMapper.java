package com.huah.ai.platform.agent.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AiAuditLogMapper extends BaseMapper<AiAuditLog> {

    @Select("SELECT * FROM ai_audit_logs WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<AiAuditLog> selectByUserIdOrderByCreatedAtDesc(@Param("userId") String userId);

    @Select("SELECT * FROM ai_audit_logs WHERE agent_type = #{agentType} AND created_at > #{after} ORDER BY created_at DESC")
    List<AiAuditLog> selectByAgentTypeAfter(@Param("agentType") String agentType, @Param("after") LocalDateTime after);

    @Select("SELECT agent_type AS agentType, COUNT(*) AS cnt, AVG(latency_ms) AS avgLatency " +
            "FROM ai_audit_logs WHERE created_at > #{since} GROUP BY agent_type")
    List<Map<String, Object>> statsGroupByAgent(@Param("since") LocalDateTime since);

    @Select("SELECT user_id AS userId, SUM(COALESCE(prompt_tokens,0) + COALESCE(completion_tokens,0)) AS totalTokens " +
            "FROM ai_audit_logs WHERE created_at > #{since} GROUP BY user_id ORDER BY totalTokens DESC")
    List<Map<String, Object>> topTokenUsersSince(@Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) FROM ai_audit_logs WHERE user_id = #{userId} AND created_at > #{after}")
    long countByUserIdAfter(@Param("userId") String userId, @Param("after") LocalDateTime after);
}
