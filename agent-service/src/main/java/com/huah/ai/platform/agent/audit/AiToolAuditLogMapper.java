package com.huah.ai.platform.agent.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiToolAuditLogMapper extends BaseMapper<AiToolAuditLog> {

    @Select("""
            <script>
            SELECT * FROM ai_tool_audit_logs
            WHERE 1 = 1
            <if test="userId != null and userId != ''">
              AND user_id = #{userId}
            </if>
            <if test="agentType != null and agentType != ''">
              AND agent_type = #{agentType}
            </if>
            <if test="toolName != null and toolName != ''">
              AND tool_name = #{toolName}
            </if>
            ORDER BY created_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<AiToolAuditLog> selectRecent(@Param("userId") String userId,
                                      @Param("agentType") String agentType,
                                      @Param("toolName") String toolName,
                                      @Param("limit") int limit);
}
