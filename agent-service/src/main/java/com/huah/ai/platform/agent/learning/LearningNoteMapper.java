package com.huah.ai.platform.agent.learning;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LearningNoteMapper extends BaseMapper<LearningNoteRecord> {

    @Select("SELECT * FROM learning_notes WHERE user_id = #{userId} ORDER BY updated_at DESC")
    List<LearningNoteRecord> selectByUserId(@Param("userId") String userId);

    @Delete("DELETE FROM learning_notes WHERE user_id = #{userId} AND id = #{id}")
    int deleteByUserIdAndId(@Param("userId") String userId, @Param("id") String id);
}
