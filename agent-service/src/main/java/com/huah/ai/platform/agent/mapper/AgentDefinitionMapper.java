package com.huah.ai.platform.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.agent.entity.AgentDefinitionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentDefinitionMapper extends BaseMapper<AgentDefinitionEntity> {

    List<AgentDefinitionEntity> selectAllDefinitions();

    List<AgentDefinitionEntity> selectEnabledDefinitions();

    AgentDefinitionEntity selectByAgentCode(@Param("agentCode") String agentCode);

    int deleteByAgentCode(@Param("agentCode") String agentCode);
}
