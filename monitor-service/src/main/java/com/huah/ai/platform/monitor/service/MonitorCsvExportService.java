package com.huah.ai.platform.monitor.service;

import com.huah.ai.platform.monitor.model.EvidenceFeedbackSampleView;
import com.huah.ai.platform.monitor.model.FailureSampleView;
import com.huah.ai.platform.monitor.model.FeedbackSampleView;
import com.huah.ai.platform.monitor.model.SlowRequestView;
import com.huah.ai.platform.monitor.model.TopUserView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
public class MonitorCsvExportService {

    private static final DateTimeFormatter CSV_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MonitorMetricsQueryService monitorMetricsQueryService;
    private final MonitorFeedbackQueryService monitorFeedbackQueryService;

    public String exportSlowRequestsCsv(int limit) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("id,userId,agentType,modelId,traceId,latencyMs,success,createdAt");
        for (SlowRequestView item : monitorMetricsQueryService.getSlowRequests(limit)) {
            joiner.add(String.join(",",
                    csv(item.getId()),
                    csv(item.getUserId()),
                    csv(item.getAgentType()),
                    csv(item.getModelId()),
                    csv(item.getTraceId()),
                    String.valueOf(item.getLatencyMs()),
                    String.valueOf(item.isSuccess()),
                    csv(formatDateTime(item.getCreatedAt()))));
        }
        return joiner.toString();
    }

    public String exportFailureSamplesCsv(int limit) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("id,userId,agentType,modelId,errorMessage,latencyMs,sessionId,traceId,createdAt");
        for (FailureSampleView item : monitorMetricsQueryService.getFailureSamples(limit)) {
            joiner.add(String.join(",",
                    csv(item.getId()),
                    csv(item.getUserId()),
                    csv(item.getAgentType()),
                    csv(item.getModelId()),
                    csv(item.getErrorMessage()),
                    String.valueOf(item.getLatencyMs()),
                    csv(item.getSessionId()),
                    csv(item.getTraceId()),
                    csv(formatDateTime(item.getCreatedAt()))));
        }
        return joiner.toString();
    }

    public String exportFeedbackCsv(int limit) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("responseId,userId,sourceType,agentType,knowledgeBaseId,feedback,comment,createdAt");
        for (FeedbackSampleView item : monitorFeedbackQueryService.getRecentFeedback(limit)) {
            joiner.add(String.join(",",
                    csv(item.getResponseId()),
                    csv(item.getUserId()),
                    csv(item.getSourceType()),
                    csv(item.getAgentType()),
                    csv(item.getKnowledgeBaseId()),
                    csv(item.getFeedback()),
                    csv(item.getComment()),
                    csv(formatDateTime(item.getCreatedAt()))));
        }
        return joiner.toString();
    }

    public String exportEvidenceFeedbackCsv(int limit) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("responseId,chunkId,userId,knowledgeBaseId,feedback,comment,createdAt");
        for (EvidenceFeedbackSampleView item : monitorFeedbackQueryService.getRecentEvidenceFeedback(limit)) {
            joiner.add(String.join(",",
                    csv(item.getResponseId()),
                    csv(item.getChunkId()),
                    csv(item.getUserId()),
                    csv(item.getKnowledgeBaseId()),
                    csv(item.getFeedback()),
                    csv(item.getComment()),
                    csv(formatDateTime(item.getCreatedAt()))));
        }
        return joiner.toString();
    }

    public String exportTopUsersCsv() {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("userId,agentType,calls,avgLatency");
        for (TopUserView item : monitorMetricsQueryService.getTopUsers()) {
            joiner.add(String.join(",",
                    csv(item.getUserId()),
                    csv(item.getAgentType()),
                    String.valueOf(item.getCalls()),
                    String.valueOf(item.getAvgLatency())));
        }
        return joiner.toString();
    }

    private String formatDateTime(LocalDateTime value) {
        return value != null ? value.format(CSV_TIME_FORMATTER) : "";
    }

    private String csv(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
