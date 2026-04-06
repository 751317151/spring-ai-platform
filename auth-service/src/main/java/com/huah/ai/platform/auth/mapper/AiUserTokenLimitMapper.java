package com.huah.ai.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.auth.model.AiUserTokenLimitEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiUserTokenLimitMapper extends BaseMapper<AiUserTokenLimitEntity> {

    List<AiUserTokenLimitEntity> selectAllViews();

    AiUserTokenLimitEntity selectViewById(@Param("id") Long id);

    AiUserTokenLimitEntity selectDuplicate(
            @Param("userId") String userId,
            @Param("botType") String botType,
            @Param("excludeId") Long excludeId);

    int deleteByUserId(@Param("userId") String userId);
}
