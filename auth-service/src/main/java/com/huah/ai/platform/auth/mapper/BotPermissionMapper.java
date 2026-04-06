package com.huah.ai.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.auth.model.BotPermissionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BotPermissionMapper extends BaseMapper<BotPermissionEntity> {

    BotPermissionEntity selectByBotTypeAndEnabled(@Param("botType") String botType);
}
