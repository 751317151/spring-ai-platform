package com.huah.ai.platform.common.persistence.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiAlertWorkflowHistoryMapper extends BaseMapper<AiAlertWorkflowHistoryEntity> {

    List<AiAlertWorkflowHistoryEntity> selectByFingerprintLimit(
            @Param("fingerprint") String fingerprint,
            @Param("limit") int limit);
}
