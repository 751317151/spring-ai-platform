package com.huah.ai.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.auth.model.AiUserRoleEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiUserRoleMapper extends BaseMapper<AiUserRoleEntity> {

    int deleteByUserId(@Param("userId") String userId);

    List<String> selectRoleNamesByUserId(@Param("userId") String userId);

    int countUsersByRoleId(@Param("roleId") Long roleId);

    List<String> selectUserReferencesByRoleId(@Param("roleId") Long roleId);

    int countEnabledUsersByRoleName(@Param("roleName") String roleName);
}
