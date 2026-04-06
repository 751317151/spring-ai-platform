package com.huah.ai.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.auth.model.AiUserEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiUserMapper extends BaseMapper<AiUserEntity> {

    AiUserEntity selectByUserId(@Param("userId") String userId);

    AiUserEntity selectByUserIdAndEnabled(@Param("userId") String userId);

    AiUserEntity selectByUsername(@Param("username") String username);

    AiUserEntity selectByUsernameAndEnabled(@Param("username") String username);

    AiUserEntity selectViewById(@Param("userId") String userId);

    List<AiUserEntity> selectAllViews();
}
