package com.huah.ai.platform.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huah.ai.platform.rag.model.KnowledgeBaseEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBaseEntity> {

    List<KnowledgeBaseEntity> selectByStatus(@Param("status") String status);

    List<KnowledgeBaseEntity> selectByDepartment(@Param("department") String department);
}
