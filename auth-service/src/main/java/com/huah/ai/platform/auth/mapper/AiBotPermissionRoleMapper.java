package com.huah.ai.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.auth.model.AiBotPermissionRoleEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiBotPermissionRoleMapper extends BaseMapper<AiBotPermissionRoleEntity> {

    int deleteByPermissionId(@Param("permissionId") Long permissionId);

    List<String> selectRoleNamesByPermissionId(@Param("permissionId") Long permissionId);

    int countPermissionsByRoleId(@Param("roleId") Long roleId);

    List<String> selectPermissionReferencesByRoleId(@Param("roleId") Long roleId);
}
