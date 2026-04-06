package com.huah.ai.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.auth.model.AiRoleEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiRoleMapper extends BaseMapper<AiRoleEntity> {

    List<AiRoleEntity> selectByRoleNames(@Param("roleNames") List<String> roleNames);
}
