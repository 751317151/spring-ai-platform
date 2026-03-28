package com.huah.ai.platform.agent.multi;

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
@TableName("ai_multi_agent_trace_steps")
public class MultiAgentExecutionStep {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String traceId;
    private Integer stepOrder;
    private String stage;
    private String agentName;
    private String inputSummary;
    private String outputSummary;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long latencyMs;
    private Boolean success;
    private String errorMessage;
    private Boolean recoverable;
    private Boolean skipped;
    private String recoveryAction;
    private String sourceTraceId;
    private Integer sourceStepOrder;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
