package com.huah.ai.platform.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.rag.model.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    @Select("SELECT * FROM knowledge_bases WHERE status = #{status}")
    List<KnowledgeBase> selectByStatus(String status);

    @Select("SELECT * FROM knowledge_bases WHERE department = #{department}")
    List<KnowledgeBase> selectByDepartment(String department);
}
