package com.huah.ai.platform.common.persistence.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiAlertWorkflowMapper extends BaseMapper<AiAlertWorkflowEntity> {

    List<AiAlertWorkflowEntity> selectByFingerprints(@Param("fingerprints") List<String> fingerprints);

    int upsert(AiAlertWorkflowEntity entity);
}
