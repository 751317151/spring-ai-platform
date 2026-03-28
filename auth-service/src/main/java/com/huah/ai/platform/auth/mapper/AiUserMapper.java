package com.huah.ai.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.auth.model.AiUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiUserMapper extends BaseMapper<AiUser> {

    AiUser selectByUsername(@Param("username") String username);

    AiUser selectByUsernameAndEnabled(@Param("username") String username);
}
