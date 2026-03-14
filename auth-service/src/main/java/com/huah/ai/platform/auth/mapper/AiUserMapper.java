package com.huah.ai.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.auth.model.AiUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiUserMapper extends BaseMapper<AiUser> {

    @Select("SELECT * FROM ai_users WHERE username = #{username}")
    AiUser selectByUsername(String username);

    @Select("SELECT * FROM ai_users WHERE username = #{username} AND enabled = true")
    AiUser selectByUsernameAndEnabled(String username);
}
