package com.huah.ai.platform.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.agent.entity.AgentRoleEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentRoleMapper extends BaseMapper<AgentRoleEntity> {

    List<String> selectRoleNamesByAgentCode(@Param("agentCode") String agentCode);

    int deleteByAgentCode(@Param("agentCode") String agentCode);
}
