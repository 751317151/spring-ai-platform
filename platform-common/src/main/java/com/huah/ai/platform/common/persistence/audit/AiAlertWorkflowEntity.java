package com.huah.ai.platform.common.persistence.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_alert_workflow")
public class AiAlertWorkflowEntity {

    @TableId(type = IdType.INPUT)
    private String fingerprint;

    private String workflowStatus;
    private String workflowNote;
    private LocalDateTime silencedUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
