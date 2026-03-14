package com.huah.ai.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.auth.model.BotPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BotPermissionMapper extends BaseMapper<BotPermission> {

    @Select("SELECT * FROM ai_bot_permissions WHERE bot_type = #{botType} AND enabled = true")
    BotPermission selectByBotTypeAndEnabled(String botType);
}
