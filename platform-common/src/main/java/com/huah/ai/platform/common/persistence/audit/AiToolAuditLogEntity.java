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
@TableName("ai_tool_audit_logs")
public class AiToolAuditLogEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String userId;
    private String sessionId;
    private String agentType;
    private String toolName;
    private String toolClass;
    private String inputSummary;
    private String outputSummary;
    private Boolean success;
    private String errorMessage;
    private String reasonCode;
    private String deniedResource;
    private Long latencyMs;
    private String traceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
