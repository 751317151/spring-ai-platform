package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.audit.AiToolAuditLogEntity;
import com.huah.ai.platform.agent.audit.AiToolAuditLogMapper;
import com.huah.ai.platform.agent.dto.ToolAuditLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentToolAuditQueryService {

    private final AiToolAuditLogMapper toolAuditLogMapper;

    public List<ToolAuditLogResponse> listRecent(String userId,
                                                 String agentType,
                                                 String toolName,
                                                 String traceId,
                                                 int limit) {
        return toolAuditLogMapper.selectRecent(userId, agentType, toolName, traceId, limit).stream()
                .map(this::toResponse)
                .toList();
    }

    private ToolAuditLogResponse toResponse(AiToolAuditLogEntity log) {
        return ToolAuditLogResponse.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .sessionId(log.getSessionId())
                .agentType(log.getAgentType())
                .toolName(log.getToolName())
                .toolClass(log.getToolClass())
                .inputSummary(log.getInputSummary())
                .outputSummary(log.getOutputSummary())
                .success(log.getSuccess())
                .errorMessage(log.getErrorMessage())
                .reasonCode(log.getReasonCode())
                .deniedResource(log.getDeniedResource())
                .latencyMs(log.getLatencyMs())
                .traceId(log.getTraceId())
                .createdAt(log.getCreatedAt())
                .build();
    }
}

