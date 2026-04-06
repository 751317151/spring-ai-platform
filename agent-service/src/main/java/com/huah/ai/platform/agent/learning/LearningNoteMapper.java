package com.huah.ai.platform.agent.learning;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LearningNoteMapper extends BaseMapper<LearningNoteEntity> {

    List<LearningNoteEntity> selectByUserId(@Param("userId") String userId);

    int deleteByUserIdAndId(@Param("userId") String userId, @Param("id") Long id);
}

