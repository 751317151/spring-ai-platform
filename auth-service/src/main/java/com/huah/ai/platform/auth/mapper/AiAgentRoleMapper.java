package com.huah.ai.platform.auth.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiAgentRoleMapper {

    List<String> selectRoleNamesByAgentCode(@Param("agentCode") String agentCode);

    int countAgentsByRoleId(@Param("roleId") Long roleId);

    List<String> selectAgentReferencesByRoleId(@Param("roleId") Long roleId);
}
