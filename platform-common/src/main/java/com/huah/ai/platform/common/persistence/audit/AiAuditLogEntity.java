package com.huah.ai.platform.common.persistence.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName("ai_audit_logs")
public class AiAuditLogEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String userId;
    private String agentType;
    private String modelId;
    private String userMessage;
    private String aiResponse;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long latencyMs;
    private Boolean success;
    private String errorMessage;
    private String clientIp;
    private String country;
    private String province;
    private String city;
    private String sessionId;
    private String traceId;

    @TableField("phase_breakdown_json")
    private String phaseBreakdownJson;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
