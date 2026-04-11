package com.huah.ai.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.auth.model.AiAgentDefinitionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiAgentDefinitionMapper extends BaseMapper<AiAgentDefinitionEntity> {

    List<AiAgentDefinitionEntity> selectEnabledDefinitions();
}
