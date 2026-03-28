package com.huah.ai.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.auth.model.BotPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BotPermissionMapper extends BaseMapper<BotPermission> {

    BotPermission selectByBotTypeAndEnabled(@Param("botType") String botType);
}
