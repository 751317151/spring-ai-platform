package com.huah.ai.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.auth.model.AiRoleTokenLimitEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiRoleTokenLimitMapper extends BaseMapper<AiRoleTokenLimitEntity> {

    List<AiRoleTokenLimitEntity> selectAllViews();

    AiRoleTokenLimitEntity selectViewById(@Param("id") Long id);

    AiRoleTokenLimitEntity selectDuplicate(
            @Param("roleId") Long roleId,
            @Param("botType") String botType,
            @Param("excludeId") Long excludeId);

    int countByRoleId(@Param("roleId") Long roleId);

    int deleteByRoleId(@Param("roleId") Long roleId);
}
