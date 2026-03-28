package com.huah.ai.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.auth.model.AiUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiUserMapper extends BaseMapper<AiUser> {

    AiUser selectByUserId(@Param("userId") String userId);

    AiUser selectByUserIdAndEnabled(@Param("userId") String userId);

    AiUser selectByUsername(@Param("username") String username);

    AiUser selectByUsernameAndEnabled(@Param("username") String username);
}
