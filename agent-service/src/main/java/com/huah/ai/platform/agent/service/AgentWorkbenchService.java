package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.audit.AiAuditLogEntity;
import com.huah.ai.platform.agent.audit.AiAuditLogMapper;
import com.huah.ai.platform.agent.audit.AiToolAuditLogEntity;
import com.huah.ai.platform.agent.audit.AiToolAuditLogMapper;
import com.huah.ai.platform.agent.dto.AgentWorkbenchChangeItem;
import com.huah.ai.platform.agent.dto.AgentWorkbenchCompareInsight;
import com.huah.ai.platform.agent.dto.AgentWorkbenchCompareAgentDetail;
import com.huah.ai.platform.agent.dto.AgentWorkbenchCompareChangeItem;
import com.huah.ai.platform.agent.dto.AgentWorkbenchCompareMetric;
import com.huah.ai.platform.agent.dto.AgentWorkbenchCompareResponse;
import com.huah.ai.platform.agent.dto.AgentWorkbenchErrorTypeItem;
import com.huah.ai.platform.agent.dto.AgentWorkbenchFailureItem;
import com.huah.ai.platform.agent.dto.AgentWorkbenchHealthSummary;
import com.huah.ai.platform.agent.dto.AgentWorkbenchSummaryResponse;
import com.huah.ai.platform.agent.dto.AgentWorkbenchToolRankItem;
import com.huah.ai.platform.agent.dto.AgentWorkbenchTrendPoint;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionTrace;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionTraceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentWorkbenchService {

    private static final int DEFAULT_FAILURE_LIMIT = 5;
    private static final int DEFAULT_TOOL_AUDIT_LIMIT = 300;
    private static final int DEFAULT_TRACE_LIMIT = 30;
    private static final int DEFAULT_TOOL_RANK_LIMIT = 6;
    private static final int DAYS_PER_WEEK = 7;
    private static final int HEALTH_WARNING_MIN_CALLS = 10;
    private static final double FAILURE_SPIKE_THRESHOLD = 0.25d;
    private static final double TOOL_FAILURE_SPIKE_THRESHOLD = 0.2d;
    private static final double TRAFFIC_GROWTH_THRESHOLD = 1.5d;
    private static final double FAILURE_RATE_GROWTH_THRESHOLD = 0.15d;
    private static final long COMPARE_CALL_GAP_THRESHOLD = 5L;
    private static final double COMPARE_FAILURE_GAP_THRESHOLD = 0.1d;
    private static final double COMPARE_FAILURE_HIGH_GAP_THRESHOLD = 0.2d;
    private static final long COMPARE_LATENCY_GAP_THRESHOLD = 80L;
    private static final long COMPARE_LATENCY_HIGH_GAP_THRESHOLD = 200L;
    private static final long COMPARE_TOOL_FAILURE_GAP_THRESHOLD = 2L;
    private static final String RISK_LEVEL_LOW = "low";
    private static final String RISK_LEVEL_MEDIUM = "medium";
    private static final String RISK_LEVEL_HIGH = "high";
    private static final String CHANGE_TYPE_TRAFFIC = "traffic";
    private static final String CHANGE_TYPE_FAILURE_RATE = "failure-rate";
    private static final String CHANGE_TYPE_LATENCY = "latency";
    private static final String CHANGE_TYPE_TOOL_FAILURE = "tool-failure";
    private static final String CHANGE_TYPE_POLICY_RISK = "policy-risk";
    private static final String CHANGE_TYPE_STABLE = "stable";
    private static final String DIRECTION_FLAT = "flat";
    private static final String DIRECTION_DIVERGED = "diverged";
    private static final String TREND_FLAT = "flat";
    private static final String TREND_LEFT_BETTER = "left-better";
    private static final String TREND_RIGHT_BETTER = "right-better";
    private static final String EMPTY_VALUE = "";
    private static final String NO_NOTABLE_CHANGE_SUMMARY = "No notable change in the current window";
    private static final String NO_ACTION_NEEDED = "No action needed.";
    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("HH:00");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter WEEK_LABEL = DateTimeFormatter.ofPattern("MM-dd");

    private final AiAuditLogMapper auditLogMapper;
    private final AiToolAuditLogMapper toolAuditLogMapper;
    private final MultiAgentExecutionTraceMapper multiAgentExecutionTraceMapper;
    private final AgentRuntimePolicyService agentRuntimePolicyService;

    public AgentWorkbenchCompareResponse compare(String leftAgentType, String rightAgentType) {
        AgentWorkbenchSummaryResponse left = build(leftAgentType);
        AgentWorkbenchSummaryResponse right = build(rightAgentType);
        List<AgentWorkbenchCompareMetric> metrics = buildCompareMetrics(left, right);
        List<AgentWorkbenchCompareInsight> insights = buildCompareInsights(left, right);
        return AgentWorkbenchCompareResponse.builder()
                .left(left)
                .right(right)
                .summary(buildCompareSummary(left, right, insights))
                .metrics(metrics)
                .insights(insights)
                .leftDetail(buildCompareAgentDetail(left))
                .rightDetail(buildCompareAgentDetail(right))
                .changeComparison(buildCompareChanges(left, right))
                .build();
    }

    public AgentWorkbenchSummaryResponse build(String agentType) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24hSince = now.minusHours(24);
        LocalDateTime last7dSince = now.minusDays(7);
        LocalDateTime last4wSince = now.minusDays(28);

        List<AiAuditLogEntity> last24hAuditLogs = auditLogMapper.selectByAgentTypeAfter(agentType, last24hSince);
        List<AiAuditLogEntity> last7dAuditLogs = auditLogMapper.selectByAgentTypeAfter(agentType, last7dSince);
        List<AiAuditLogEntity> last4wAuditLogs = auditLogMapper.selectByAgentTypeAfter(agentType, last4wSince);
        List<AiToolAuditLogEntity> last24hToolAudits = toolAuditLogMapper.selectRecentByAgentTypeAfter(agentType, last24hSince, DEFAULT_TOOL_AUDIT_LIMIT);
        List<AiToolAuditLogEntity> last7dToolAudits = toolAuditLogMapper.selectRecentByAgentTypeAfter(agentType, last7dSince, DEFAULT_TOOL_AUDIT_LIMIT * 2);
        List<AiToolAuditLogEntity> last4wToolAudits = toolAuditLogMapper.selectRecentByAgentTypeAfter(agentType, last4wSince, DEFAULT_TOOL_AUDIT_LIMIT * 4);
        List<MultiAgentExecutionTrace> traces = "multi".equals(agentType)
                ? multiAgentExecutionTraceMapper.selectRecentAfter(last24hSince, DEFAULT_TRACE_LIMIT)
                : List.of();

        long totalCalls = last24hAuditLogs.size();
        long failureCalls = countFailedAuditLogs(last24hAuditLogs);
        long avgLatencyMs = averageAuditLatency(last24hAuditLogs);

        long toolCallCount = last24hToolAudits.size();
        long toolFailureCount = countFailedToolAudits(last24hToolAudits);
        long avgToolLatencyMs = averageToolLatency(last24hToolAudits);
        AiToolAuditLogEntity slowestTool = last24hToolAudits.stream()
                .max(Comparator.comparingLong(item -> item.getLatencyMs() == null ? 0L : item.getLatencyMs()))
                .orElse(null);

        String latestTraceId = traces.isEmpty() ? "" : nullToEmpty(traces.get(0).getTraceId());
        List<AgentWorkbenchFailureItem> recentFailures = buildRecentFailures(last24hAuditLogs);
        String latestErrorMessage = recentFailures.isEmpty() ? "" : nullToEmpty(recentFailures.get(0).getErrorMessage());

        List<AgentWorkbenchTrendPoint> last24hTrend = buildHourlyTrend(last24hAuditLogs, last24hToolAudits, now);
        List<AgentWorkbenchTrendPoint> last7dTrend = buildDailyTrend(last7dAuditLogs, last7dToolAudits, now);
        List<AgentWorkbenchTrendPoint> last4wTrend = buildWeeklyTrend(last4wAuditLogs, last4wToolAudits, now);
        List<AgentWorkbenchToolRankItem> toolRanking = buildToolRanking(last24hToolAudits);
        List<AgentWorkbenchErrorTypeItem> errorTypes = buildErrorTypes(last24hAuditLogs, last24hToolAudits);
        AgentWorkbenchHealthSummary healthSummary = buildHealthSummary(totalCalls, failureCalls, toolCallCount, toolFailureCount);
        List<AgentWorkbenchChangeItem> recentChanges = buildRecentChanges(last24hAuditLogs, last7dAuditLogs, last24hToolAudits, last7dToolAudits);
        String weeklyDigest = buildWeeklyDigest(last4wTrend);

        return AgentWorkbenchSummaryResponse.builder()
                .agentType(agentType)
                .windowLabel("最近 24 小时")
                .totalCalls(totalCalls)
                .failureCalls(failureCalls)
                .successRate(totalCalls == 0 ? 0d : ((double) (totalCalls - failureCalls) / totalCalls))
                .avgLatencyMs(avgLatencyMs)
                .toolCallCount(toolCallCount)
                .toolFailureCount(toolFailureCount)
                .avgToolLatencyMs(avgToolLatencyMs)
                .slowestToolName(slowestTool == null ? "" : nullToEmpty(firstNonBlank(slowestTool.getToolName(), slowestTool.getToolClass())))
                .slowestToolLatencyMs(slowestTool == null || slowestTool.getLatencyMs() == null ? 0L : slowestTool.getLatencyMs())
                .recentTraceCount(traces.size())
                .latestTraceId(latestTraceId)
                .latestErrorMessage(latestErrorMessage)
                .healthSummary(healthSummary)
                .runtimePolicySummary(agentRuntimePolicyService.build(agentType))
                .last24hTrend(last24hTrend)
                .last7dTrend(last7dTrend)
                .last4wTrend(last4wTrend)
                .toolRanking(toolRanking)
                .errorTypes(errorTypes)
                .recentChanges(recentChanges)
                .weeklyDigest(weeklyDigest)
                .recentFailures(recentFailures)
                .build();
    }

    private List<AgentWorkbenchCompareInsight> buildCompareInsights(AgentWorkbenchSummaryResponse left,
                                                                    AgentWorkbenchSummaryResponse right) {
        List<AgentWorkbenchCompareInsight> insights = new ArrayList<>();

        long callGap = Math.abs(left.getTotalCalls() - right.getTotalCalls());
        if (callGap >= COMPARE_CALL_GAP_THRESHOLD) {
            boolean leftHigher = left.getTotalCalls() > right.getTotalCalls();
            insights.add(compareInsight(
                    CHANGE_TYPE_TRAFFIC,
                    RISK_LEVEL_MEDIUM,
                    leftHigher ? left.getAgentType() : right.getAgentType(),
                    leftHigher ? right.getAgentType() : left.getAgentType(),
                    "total-calls",
                    "Call volume gap",
                    "The leading agent handled " + callGap + " more requests in the current window",
                    String.valueOf(left.getTotalCalls()),
                    String.valueOf(right.getTotalCalls()),
                    "Traffic skew often means one agent is taking more real workloads or is favored by routing",
                    "Check whether the lower-traffic side is intentionally gated, or whether routing confidence and availability need adjustment"
            ));
        }

        double leftFailureRate = left.getTotalCalls() == 0 ? 0d : (double) left.getFailureCalls() / left.getTotalCalls();
        double rightFailureRate = right.getTotalCalls() == 0 ? 0d : (double) right.getFailureCalls() / right.getTotalCalls();
        double failureGap = Math.abs(leftFailureRate - rightFailureRate);
        if (failureGap >= COMPARE_FAILURE_GAP_THRESHOLD) {
            boolean leftWorse = leftFailureRate > rightFailureRate;
            insights.add(compareInsight(
                    CHANGE_TYPE_FAILURE_RATE,
                    failureGap >= COMPARE_FAILURE_HIGH_GAP_THRESHOLD ? RISK_LEVEL_HIGH : RISK_LEVEL_MEDIUM,
                    leftWorse ? left.getAgentType() : right.getAgentType(),
                    leftWorse ? right.getAgentType() : left.getAgentType(),
                    CHANGE_TYPE_FAILURE_RATE,
                    "Failure rate gap",
                    "The worse side is higher by " + Math.round(failureGap * 100) + "% failure rate",
                    formatPercentLabel(leftFailureRate),
                    formatPercentLabel(rightFailureRate),
                    "Failure rate directly affects user-visible stability and retry pressure",
                    "Inspect recent failure samples, tool errors and dependency spikes on the worse side first"
            ));
        }

        long latencyGap = Math.abs(left.getAvgLatencyMs() - right.getAvgLatencyMs());
        if (latencyGap >= COMPARE_LATENCY_GAP_THRESHOLD) {
            boolean leftSlower = left.getAvgLatencyMs() > right.getAvgLatencyMs();
            insights.add(compareInsight(
                    CHANGE_TYPE_LATENCY,
                    latencyGap >= COMPARE_LATENCY_HIGH_GAP_THRESHOLD ? RISK_LEVEL_HIGH : RISK_LEVEL_MEDIUM,
                    leftSlower ? left.getAgentType() : right.getAgentType(),
                    leftSlower ? right.getAgentType() : left.getAgentType(),
                    "avg-latency",
                    "Latency gap",
                    "The slower side is behind by " + latencyGap + " ms on average",
                    left.getAvgLatencyMs() + " ms",
                    right.getAvgLatencyMs() + " ms",
                    "Latency gap usually points to model cost, tool chains or slow dependencies",
                    "Compare slowest tools, model latency and recent traffic spikes before tuning prompts"
            ));
        }

        long toolFailureGap = Math.abs(left.getToolFailureCount() - right.getToolFailureCount());
        if (toolFailureGap >= COMPARE_TOOL_FAILURE_GAP_THRESHOLD) {
            boolean leftWorse = left.getToolFailureCount() > right.getToolFailureCount();
            insights.add(compareInsight(
                    CHANGE_TYPE_TOOL_FAILURE,
                    RISK_LEVEL_MEDIUM,
                    leftWorse ? left.getAgentType() : right.getAgentType(),
                    leftWorse ? right.getAgentType() : left.getAgentType(),
                    "tool-failures",
                    "Tool failure gap",
                    "The worse side shows " + toolFailureGap + " more tool failures in the current window",
                    String.valueOf(left.getToolFailureCount()),
                    String.valueOf(right.getToolFailureCount()),
                    "Tool failures often amplify user failure rate and create unstable agent behavior",
                    "Check connector permissions, MCP tool availability and recent dependency errors on the worse side"
            ));
        }

        int leftRisk = left.getRuntimePolicySummary() != null ? left.getRuntimePolicySummary().getRiskCount() : 0;
        int rightRisk = right.getRuntimePolicySummary() != null ? right.getRuntimePolicySummary().getRiskCount() : 0;
        int riskGap = Math.abs(leftRisk - rightRisk);
        if (riskGap >= 1) {
            boolean leftHigher = leftRisk > rightRisk;
            insights.add(compareInsight(
                    CHANGE_TYPE_POLICY_RISK,
                    RISK_LEVEL_LOW,
                    leftHigher ? left.getAgentType() : right.getAgentType(),
                    leftHigher ? right.getAgentType() : left.getAgentType(),
                    CHANGE_TYPE_POLICY_RISK,
                    "Policy risk gap",
                    "The higher risk side has " + riskGap + " more governance risk indicators",
                    String.valueOf(leftRisk),
                    String.valueOf(rightRisk),
                    "Governance risk reflects looser boundaries and potentially wider blast radius",
                    "Compare wildcard permissions, restricted resources and runtime policy summaries before widening access"
            ));
        }

        if (insights.isEmpty()) {
            insights.add(compareInsight(
                    CHANGE_TYPE_STABLE,
                    RISK_LEVEL_LOW,
                    left.getAgentType(),
                    right.getAgentType(),
                    CHANGE_TYPE_STABLE,
                    "No strong gap",
                    "No major operational gap was detected between the two agents in the current window",
                    left.getAgentType(),
                    right.getAgentType(),
                    "Both agents are behaving within a similar operational range",
                    "Use metric deltas and recent failures only if you need a deeper tie-breaker"
            ));
        }
        return insights;
    }

    private String buildCompareSummary(AgentWorkbenchSummaryResponse left,
                                       AgentWorkbenchSummaryResponse right,
                                       List<AgentWorkbenchCompareInsight> insights) {
        AgentWorkbenchCompareInsight primary = insights.get(0);
        return left.getAgentType() + " vs " + right.getAgentType()
                + ", primaryGap=" + primary.getType()
                + ", severity=" + primary.getSeverity();
    }

    private AgentWorkbenchCompareAgentDetail buildCompareAgentDetail(AgentWorkbenchSummaryResponse summary) {
        double failureRate = summary.getTotalCalls() == 0
                ? 0d
                : (double) summary.getFailureCalls() / summary.getTotalCalls();
        return AgentWorkbenchCompareAgentDetail.builder()
                .agentType(summary.getAgentType())
                .summary(summary.getWeeklyDigest())
                .healthSummary(summary.getHealthSummary() != null ? summary.getHealthSummary().getSummary() : "")
                .policySummary(summary.getRuntimePolicySummary() != null ? summary.getRuntimePolicySummary().getSummary() : "")
                .totalCalls(summary.getTotalCalls())
                .failureRateLabel(formatPercentLabel(failureRate))
                .riskLevel(summary.getRuntimePolicySummary() != null ? nullToEmpty(summary.getRuntimePolicySummary().getRiskLevel()) : RISK_LEVEL_LOW)
                .highlights(summary.getRuntimePolicySummary() != null && summary.getRuntimePolicySummary().getHighlights() != null
                        ? summary.getRuntimePolicySummary().getHighlights()
                        : List.<String>of())
                .topErrorTypes(extractTopErrorTypes(summary))
                .recentChanges(summary.getRecentChanges() != null ? summary.getRecentChanges() : List.<AgentWorkbenchChangeItem>of())
                .recentFailures(summary.getRecentFailures() != null ? summary.getRecentFailures() : List.<AgentWorkbenchFailureItem>of())
                .build();
    }

    private List<String> extractTopErrorTypes(AgentWorkbenchSummaryResponse summary) {
        if (summary.getErrorTypes() == null || summary.getErrorTypes().isEmpty()) {
            return List.of();
        }
        return summary.getErrorTypes().stream()
                .limit(3)
                .map(item -> firstNonBlank(item.getLabel(), item.getType()) + "=" + item.getCount())
                .toList();
    }

    private List<AgentWorkbenchCompareChangeItem> buildCompareChanges(AgentWorkbenchSummaryResponse left,
                                                                      AgentWorkbenchSummaryResponse right) {
        Map<String, AgentWorkbenchChangeItem> leftChanges = indexChanges(left.getRecentChanges());
        Map<String, AgentWorkbenchChangeItem> rightChanges = indexChanges(right.getRecentChanges());
        List<String> keys = new ArrayList<>();
        keys.addAll(leftChanges.keySet());
        rightChanges.keySet().stream().filter(key -> !keys.contains(key)).forEach(keys::add);
        if (keys.isEmpty()) {
            return List.of();
        }
        return keys.stream()
                .map(key -> {
                    AgentWorkbenchChangeItem leftItem = leftChanges.get(key);
                    AgentWorkbenchChangeItem rightItem = rightChanges.get(key);
                    return AgentWorkbenchCompareChangeItem.builder()
                            .type(key)
                            .label(leftItem != null ? leftItem.getLabel() : rightItem != null ? rightItem.getLabel() : key)
                            .leftSummary(leftItem != null ? leftItem.getSummary() : NO_NOTABLE_CHANGE_SUMMARY)
                            .rightSummary(rightItem != null ? rightItem.getSummary() : NO_NOTABLE_CHANGE_SUMMARY)
                            .direction(resolveCompareDirection(leftItem, rightItem))
                            .severity(resolveCompareSeverity(leftItem, rightItem))
                            .suggestedAction(buildChangeSuggestedAction(leftItem, rightItem))
                            .build();
                })
                .toList();
    }

    private Map<String, AgentWorkbenchChangeItem> indexChanges(List<AgentWorkbenchChangeItem> items) {
        Map<String, AgentWorkbenchChangeItem> result = new LinkedHashMap<>();
        if (items == null) {
            return result;
        }
        for (AgentWorkbenchChangeItem item : items) {
            if (item != null && item.getType() != null) {
                result.putIfAbsent(item.getType(), item);
            }
        }
        return result;
    }

    private String resolveCompareDirection(AgentWorkbenchChangeItem left, AgentWorkbenchChangeItem right) {
        if (left != null && right != null && !nullToEmpty(left.getDirection()).equals(nullToEmpty(right.getDirection()))) {
            return DIRECTION_DIVERGED;
        }
        if (left != null) {
            return nullToEmpty(left.getDirection());
        }
        if (right != null) {
            return nullToEmpty(right.getDirection());
        }
        return DIRECTION_FLAT;
    }

    private String resolveCompareSeverity(AgentWorkbenchChangeItem left, AgentWorkbenchChangeItem right) {
        String leftSeverity = left == null ? RISK_LEVEL_LOW : nullToEmpty(left.getSeverity());
        String rightSeverity = right == null ? RISK_LEVEL_LOW : nullToEmpty(right.getSeverity());
        if (RISK_LEVEL_HIGH.equalsIgnoreCase(leftSeverity) || RISK_LEVEL_HIGH.equalsIgnoreCase(rightSeverity)) {
            return RISK_LEVEL_HIGH;
        }
        if (RISK_LEVEL_MEDIUM.equalsIgnoreCase(leftSeverity) || RISK_LEVEL_MEDIUM.equalsIgnoreCase(rightSeverity)) {
            return RISK_LEVEL_MEDIUM;
        }
        return RISK_LEVEL_LOW;
    }

    private String buildChangeSuggestedAction(AgentWorkbenchChangeItem left, AgentWorkbenchChangeItem right) {
        if (left != null && right != null) {
            return "Compare recent failure samples and policy highlights on both sides before changing routing.";
        }
        if (left != null) {
            return "Inspect the left-side recent failures and recent trend before widening traffic.";
        }
        if (right != null) {
            return "Inspect the right-side recent failures and recent trend before widening traffic.";
        }
        return NO_ACTION_NEEDED;
    }

    private List<AgentWorkbenchCompareMetric> buildCompareMetrics(AgentWorkbenchSummaryResponse left,
                                                                  AgentWorkbenchSummaryResponse right) {
        List<AgentWorkbenchCompareMetric> metrics = new ArrayList<>();
        metrics.add(compareMetric(
                "total-calls",
                "Call volume",
                String.valueOf(left.getTotalCalls()),
                String.valueOf(right.getTotalCalls()),
                formatSignedLong(left.getTotalCalls() - right.getTotalCalls()),
                higherBetterTrend(left.getTotalCalls(), right.getTotalCalls()),
                winnerAgentType(left.getTotalCalls(), right.getTotalCalls(), left.getAgentType(), right.getAgentType()),
                "Higher request volume usually means the agent is taking more live traffic"
        ));

        double leftFailureRate = left.getTotalCalls() == 0 ? 0d : (double) left.getFailureCalls() / left.getTotalCalls();
        double rightFailureRate = right.getTotalCalls() == 0 ? 0d : (double) right.getFailureCalls() / right.getTotalCalls();
        metrics.add(compareMetric(
                "failure-rate",
                "Failure rate",
                formatPercentLabel(leftFailureRate),
                formatPercentLabel(rightFailureRate),
                formatSignedPercent(leftFailureRate - rightFailureRate),
                lowerBetterTrend(leftFailureRate, rightFailureRate),
                winnerAgentType(rightFailureRate, leftFailureRate, left.getAgentType(), right.getAgentType()),
                "Lower failure rate means the agent is currently more stable"
        ));

        metrics.add(compareMetric(
                "avg-latency",
                "Average latency",
                left.getAvgLatencyMs() + " ms",
                right.getAvgLatencyMs() + " ms",
                formatSignedLong(left.getAvgLatencyMs() - right.getAvgLatencyMs()) + " ms",
                lowerBetterTrend(left.getAvgLatencyMs(), right.getAvgLatencyMs()),
                winnerAgentType(right.getAvgLatencyMs(), left.getAvgLatencyMs(), left.getAgentType(), right.getAgentType()),
                "Lower average latency means faster responses"
        ));

        metrics.add(compareMetric(
                "tool-failures",
                "Tool failures",
                String.valueOf(left.getToolFailureCount()),
                String.valueOf(right.getToolFailureCount()),
                formatSignedLong(left.getToolFailureCount() - right.getToolFailureCount()),
                lowerBetterTrend(left.getToolFailureCount(), right.getToolFailureCount()),
                winnerAgentType(right.getToolFailureCount(), left.getToolFailureCount(), left.getAgentType(), right.getAgentType()),
                "Lower tool failure count means downstream integrations are healthier"
        ));

        int leftRisk = left.getRuntimePolicySummary() != null ? left.getRuntimePolicySummary().getRiskCount() : 0;
        int rightRisk = right.getRuntimePolicySummary() != null ? right.getRuntimePolicySummary().getRiskCount() : 0;
        metrics.add(compareMetric(
                "policy-risk",
                "Policy risk",
                String.valueOf(leftRisk),
                String.valueOf(rightRisk),
                formatSignedLong(leftRisk - rightRisk),
                lowerBetterTrend(leftRisk, rightRisk),
                winnerAgentType(rightRisk, leftRisk, left.getAgentType(), right.getAgentType()),
                "Lower risk count means tighter runtime governance boundaries"
        ));
        return metrics;
    }

    private AgentWorkbenchCompareInsight compareInsight(String type,
                                                        String severity,
                                                        String winnerAgentType,
                                                        String loserAgentType,
                                                        String metricKey,
                                                        String title,
                                                        String summary,
                                                        String leftEvidence,
                                                        String rightEvidence,
                                                        String whyItMatters,
                                                        String suggestedAction) {
        return AgentWorkbenchCompareInsight.builder()
                .type(type)
                .severity(severity)
                .winnerAgentType(winnerAgentType)
                .loserAgentType(loserAgentType)
                .metricKey(metricKey)
                .title(title)
                .summary(summary)
                .leftEvidence(leftEvidence)
                .rightEvidence(rightEvidence)
                .whyItMatters(whyItMatters)
                .suggestedAction(suggestedAction)
                .build();
    }

    private AgentWorkbenchCompareMetric compareMetric(String key,
                                                     String label,
                                                     String leftValue,
                                                     String rightValue,
                                                     String delta,
                                                     String trend,
                                                     String winnerAgentType,
                                                     String summary) {
        return AgentWorkbenchCompareMetric.builder()
                .key(key)
                .label(label)
                .leftValue(leftValue)
                .rightValue(rightValue)
                .delta(delta)
                .trend(trend)
                .winnerAgentType(winnerAgentType)
                .summary(summary)
                .build();
    }

    private String formatPercentLabel(double value) {
        return Math.round(value * 100) + "%";
    }

    private String formatSignedPercent(double value) {
        long rounded = Math.round(value * 100);
        return (rounded >= 0 ? "+" : "") + rounded + "%";
    }

    private String formatSignedLong(long value) {
        return (value >= 0 ? "+" : "") + value;
    }

    private String higherBetterTrend(double left, double right) {
        if (left == right) {
            return TREND_FLAT;
        }
        return left > right ? TREND_LEFT_BETTER : TREND_RIGHT_BETTER;
    }

    private String lowerBetterTrend(double left, double right) {
        if (left == right) {
            return TREND_FLAT;
        }
        return left < right ? TREND_LEFT_BETTER : TREND_RIGHT_BETTER;
    }

    private String winnerAgentType(double left, double right, String leftAgentType, String rightAgentType) {
        if (left == right) {
            return EMPTY_VALUE;
        }
        return left > right ? leftAgentType : rightAgentType;
    }

    private List<AgentWorkbenchFailureItem> buildRecentFailures(List<AiAuditLogEntity> auditLogs) {
        return auditLogs.stream()
                .filter(item -> Boolean.FALSE.equals(item.getSuccess()))
                .sorted(Comparator.comparing(AiAuditLogEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(DEFAULT_FAILURE_LIMIT)
                .map(item -> AgentWorkbenchFailureItem.builder()
                        .traceId(item.getTraceId())
                        .sessionId(item.getSessionId())
                        .userId(item.getUserId())
                        .summary(item.getUserMessage())
                        .errorMessage(item.getErrorMessage())
                        .latencyMs(item.getLatencyMs())
                        .createdAt(item.getCreatedAt())
                        .build())
                .toList();
    }

    private List<AgentWorkbenchTrendPoint> buildHourlyTrend(List<AiAuditLogEntity> auditLogs,
                                                            List<AiToolAuditLogEntity> toolAudits,
                                                            LocalDateTime now) {
        Map<String, List<AiAuditLogEntity>> auditByHour = new LinkedHashMap<>();
        Map<String, List<AiToolAuditLogEntity>> toolByHour = new LinkedHashMap<>();
        for (int i = 23; i >= 0; i--) {
            String label = now.minusHours(i).withMinute(0).withSecond(0).withNano(0).format(HOUR_LABEL);
            auditByHour.put(label, new ArrayList<>());
            toolByHour.put(label, new ArrayList<>());
        }
        for (AiAuditLogEntity log : auditLogs) {
            if (log.getCreatedAt() != null) {
                String label = log.getCreatedAt().withMinute(0).withSecond(0).withNano(0).format(HOUR_LABEL);
                auditByHour.computeIfAbsent(label, key -> new ArrayList<>()).add(log);
            }
        }
        for (AiToolAuditLogEntity log : toolAudits) {
            if (log.getCreatedAt() != null) {
                String label = log.getCreatedAt().withMinute(0).withSecond(0).withNano(0).format(HOUR_LABEL);
                toolByHour.computeIfAbsent(label, key -> new ArrayList<>()).add(log);
            }
        }
        return auditByHour.entrySet().stream()
                .map(entry -> toTrendPoint(entry.getKey(), entry.getValue(), toolByHour.getOrDefault(entry.getKey(), List.of())))
                .toList();
    }

    private List<AgentWorkbenchTrendPoint> buildDailyTrend(List<AiAuditLogEntity> auditLogs,
                                                           List<AiToolAuditLogEntity> toolAudits,
                                                           LocalDateTime now) {
        Map<String, List<AiAuditLogEntity>> auditByDay = new LinkedHashMap<>();
        Map<String, List<AiToolAuditLogEntity>> toolByDay = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            String label = now.toLocalDate().minusDays(i).format(DAY_LABEL);
            auditByDay.put(label, new ArrayList<>());
            toolByDay.put(label, new ArrayList<>());
        }
        for (AiAuditLogEntity log : auditLogs) {
            if (log.getCreatedAt() != null) {
                String label = log.getCreatedAt().toLocalDate().format(DAY_LABEL);
                auditByDay.computeIfAbsent(label, key -> new ArrayList<>()).add(log);
            }
        }
        for (AiToolAuditLogEntity log : toolAudits) {
            if (log.getCreatedAt() != null) {
                String label = log.getCreatedAt().toLocalDate().format(DAY_LABEL);
                toolByDay.computeIfAbsent(label, key -> new ArrayList<>()).add(log);
            }
        }
        return auditByDay.entrySet().stream()
                .map(entry -> toTrendPoint(entry.getKey(), entry.getValue(), toolByDay.getOrDefault(entry.getKey(), List.of())))
                .toList();
    }

    private List<AgentWorkbenchTrendPoint> buildWeeklyTrend(List<AiAuditLogEntity> auditLogs,
                                                            List<AiToolAuditLogEntity> toolAudits,
                                                            LocalDateTime now) {
        Map<String, List<AiAuditLogEntity>> auditByWeek = new LinkedHashMap<>();
        Map<String, List<AiToolAuditLogEntity>> toolByWeek = new LinkedHashMap<>();
        for (int i = 3; i >= 0; i--) {
            LocalDate weekStart = now.toLocalDate().minusWeeks(i).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            String label = weekLabel(weekStart);
            auditByWeek.put(label, new ArrayList<>());
            toolByWeek.put(label, new ArrayList<>());
        }
        for (AiAuditLogEntity log : auditLogs) {
            if (log.getCreatedAt() != null) {
                String label = weekLabel(log.getCreatedAt().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
                auditByWeek.computeIfAbsent(label, key -> new ArrayList<>()).add(log);
            }
        }
        for (AiToolAuditLogEntity log : toolAudits) {
            if (log.getCreatedAt() != null) {
                String label = weekLabel(log.getCreatedAt().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
                toolByWeek.computeIfAbsent(label, key -> new ArrayList<>()).add(log);
            }
        }
        return auditByWeek.entrySet().stream()
                .map(entry -> toTrendPoint(entry.getKey(), entry.getValue(), toolByWeek.getOrDefault(entry.getKey(), List.of())))
                .toList();
    }

    private AgentWorkbenchTrendPoint toTrendPoint(String label, List<AiAuditLogEntity> auditLogs, List<AiToolAuditLogEntity> toolAudits) {
        return AgentWorkbenchTrendPoint.builder()
                .label(label)
                .totalCalls(auditLogs.size())
                .failureCalls(countFailedAuditLogs(auditLogs))
                .toolCalls(toolAudits.size())
                .avgLatencyMs(averageAuditLatency(auditLogs))
                .build();
    }

    private List<AgentWorkbenchToolRankItem> buildToolRanking(List<AiToolAuditLogEntity> toolAudits) {
        Map<String, List<AiToolAuditLogEntity>> grouped = new LinkedHashMap<>();
        for (AiToolAuditLogEntity log : toolAudits) {
            String key = nullToEmpty(firstNonBlank(log.getToolName(), log.getToolClass()));
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(log);
        }
        return grouped.entrySet().stream()
                .map(entry -> {
                    List<AiToolAuditLogEntity> logs = entry.getValue();
                    AiToolAuditLogEntity latest = logs.stream()
                            .max(Comparator.comparing(AiToolAuditLogEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                            .orElse(null);
                    return AgentWorkbenchToolRankItem.builder()
                            .toolName(entry.getKey())
                            .callCount(logs.size())
                            .failureCount(countFailedToolAudits(logs))
                            .avgLatencyMs(averageToolLatency(logs))
                            .latestTraceId(latest == null ? "" : nullToEmpty(latest.getTraceId()))
                            .build();
                })
                .sorted(Comparator
                        .comparingLong(AgentWorkbenchToolRankItem::getCallCount).reversed()
                        .thenComparingLong(AgentWorkbenchToolRankItem::getFailureCount).reversed())
                .limit(DEFAULT_TOOL_RANK_LIMIT)
                .toList();
    }

    private List<AgentWorkbenchErrorTypeItem> buildErrorTypes(List<AiAuditLogEntity> auditLogs, List<AiToolAuditLogEntity> toolAudits) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("PERMISSION_DENIED", 0L);
        counts.put("MODEL_ERROR", 0L);
        counts.put("TOOL_ERROR", 0L);
        counts.put("DEPENDENCY_ERROR", 0L);

        for (AiAuditLogEntity log : auditLogs) {
            if (!Boolean.FALSE.equals(log.getSuccess())) {
                continue;
            }
            String type = classifyAuditError(log.getErrorMessage());
            counts.put(type, counts.getOrDefault(type, 0L) + 1);
        }
        for (AiToolAuditLogEntity log : toolAudits) {
            if (!Boolean.FALSE.equals(log.getSuccess())) {
                continue;
            }
            String type = classifyToolError(log.getReasonCode(), log.getErrorMessage());
            counts.put(type, counts.getOrDefault(type, 0L) + 1);
        }

        return counts.entrySet().stream()
                .map(entry -> AgentWorkbenchErrorTypeItem.builder()
                        .type(entry.getKey())
                        .label(toErrorLabel(entry.getKey()))
                        .count(entry.getValue())
                        .build())
                .sorted(Comparator.comparingLong(AgentWorkbenchErrorTypeItem::getCount).reversed())
                .toList();
    }

    private AgentWorkbenchHealthSummary buildHealthSummary(long totalCalls,
                                                           long failureCalls,
                                                           long toolCallCount,
                                                           long toolFailureCount) {
        double failureRate = totalCalls == 0 ? 0d : ((double) failureCalls / totalCalls);
        double toolFailureRate = toolCallCount == 0 ? 0d : ((double) toolFailureCount / toolCallCount);
        boolean failureSpike = totalCalls >= HEALTH_WARNING_MIN_CALLS && failureRate >= FAILURE_SPIKE_THRESHOLD;
        boolean toolFailureSpike = toolCallCount >= HEALTH_WARNING_MIN_CALLS && toolFailureRate >= TOOL_FAILURE_SPIKE_THRESHOLD;
        boolean warning = failureSpike || toolFailureSpike;
        String summary;
        if (!warning) {
            summary = "当前助手状态稳定，最近未出现明显失败峰值。";
        } else if (failureSpike && toolFailureSpike) {
            summary = "当前助手存在请求失败峰值和工具失败峰值，建议优先排查依赖和权限链路。";
        } else if (failureSpike) {
            summary = "当前助手请求失败率偏高，建议优先查看失败样本与错误分布。";
        } else {
            summary = "当前助手工具调用失败率偏高，建议优先查看工具排行榜和权限拒绝样本。";
        }
        return AgentWorkbenchHealthSummary.builder()
                .accessible(true)
                .failureSpike(failureSpike)
                .toolFailureSpike(toolFailureSpike)
                .warning(warning)
                .summary(summary)
                .build();
    }

    private List<AgentWorkbenchChangeItem> buildRecentChanges(List<AiAuditLogEntity> last24hAuditLogs,
                                                              List<AiAuditLogEntity> last7dAuditLogs,
                                                              List<AiToolAuditLogEntity> last24hToolAudits,
                                                              List<AiToolAuditLogEntity> last7dToolAudits) {
        List<AgentWorkbenchChangeItem> items = new ArrayList<>();
        double dailyBaselineCalls = last7dAuditLogs.isEmpty() ? 0d : (double) last7dAuditLogs.size() / DAYS_PER_WEEK;
        if (last24hAuditLogs.size() >= Math.max(COMPARE_CALL_GAP_THRESHOLD, dailyBaselineCalls * TRAFFIC_GROWTH_THRESHOLD)) {
            items.add(AgentWorkbenchChangeItem.builder()
                    .type("traffic")
                    .label("调用量上升")
                    .direction("up")
                    .severity("medium")
                    .summary("最近 24 小时调用量高于近 7 天日均水平，建议关注是否出现流量尖峰。")
                    .build());
        }

        double last24hFailureRate = last24hAuditLogs.isEmpty() ? 0d : (double) countFailedAuditLogs(last24hAuditLogs) / last24hAuditLogs.size();
        double last7dFailureRate = last7dAuditLogs.isEmpty() ? 0d : (double) countFailedAuditLogs(last7dAuditLogs) / last7dAuditLogs.size();
        if (last24hFailureRate - last7dFailureRate >= FAILURE_RATE_GROWTH_THRESHOLD) {
            items.add(AgentWorkbenchChangeItem.builder()
                    .type("failure-rate")
                    .label("失败率抬升")
                    .direction("up")
                    .severity("high")
                    .summary("最近 24 小时失败率显著高于近 7 天基线，建议优先查看失败样本和错误分布。")
                    .build());
        }

        long last24hPermissionDenied = last24hToolAudits.stream()
                .filter(item -> Boolean.FALSE.equals(item.getSuccess()))
                .filter(item -> item.getReasonCode() != null && item.getReasonCode().toUpperCase(Locale.ROOT).contains("DENIED"))
                .count();
        long last7dPermissionDenied = last7dToolAudits.stream()
                .filter(item -> Boolean.FALSE.equals(item.getSuccess()))
                .filter(item -> item.getReasonCode() != null && item.getReasonCode().toUpperCase(Locale.ROOT).contains("DENIED"))
                .count();
        if (last24hPermissionDenied > 0 && last24hPermissionDenied * 3 >= Math.max(1L, last7dPermissionDenied)) {
            items.add(AgentWorkbenchChangeItem.builder()
                    .type("permission")
                    .label("权限拒绝增多")
                    .direction("up")
                    .severity("medium")
                    .summary("最近 24 小时权限拒绝占比偏高，建议检查资源边界、MCP 工具范围和数据范围配置。")
                    .build());
        }

        if (items.isEmpty()) {
            items.add(AgentWorkbenchChangeItem.builder()
                    .type("stable")
                    .label("近期无明显异常波动")
                    .direction("flat")
                    .severity("low")
                    .summary("当前调用量、失败率和工具拒绝量均未明显偏离近 7 天基线。")
                    .build());
        }
        return items;
    }

    private String buildWeeklyDigest(List<AgentWorkbenchTrendPoint> last4wTrend) {
        if (last4wTrend == null || last4wTrend.isEmpty()) {
            return "最近 4 周暂无足够数据生成周报摘要。";
        }
        AgentWorkbenchTrendPoint latestWeek = last4wTrend.get(last4wTrend.size() - 1);
        AgentWorkbenchTrendPoint highestWeek = last4wTrend.stream()
                .max(Comparator.comparingLong(AgentWorkbenchTrendPoint::getTotalCalls))
                .orElse(latestWeek);
        return "最近一周调用 " + latestWeek.getTotalCalls()
                + " 次，失败 " + latestWeek.getFailureCalls()
                + " 次；近 4 周最高活跃周为 " + highestWeek.getLabel()
                + "，调用 " + highestWeek.getTotalCalls() + " 次。";
    }

    private String weekLabel(LocalDate weekStart) {
        return weekStart.format(WEEK_LABEL) + " ~ " + weekStart.plusDays(6).format(WEEK_LABEL);
    }

    private String classifyAuditError(String errorMessage) {
        String normalized = nullToEmpty(errorMessage).toLowerCase(Locale.ROOT);
        if (normalized.contains("权限") || normalized.contains("forbidden") || normalized.contains("denied")) {
            return "PERMISSION_DENIED";
        }
        if (normalized.contains("timeout") || normalized.contains("connection") || normalized.contains("network")) {
            return "DEPENDENCY_ERROR";
        }
        return "MODEL_ERROR";
    }

    private String classifyToolError(String reasonCode, String errorMessage) {
        if (reasonCode != null && reasonCode.toUpperCase(Locale.ROOT).contains("DENIED")) {
            return "PERMISSION_DENIED";
        }
        String normalized = nullToEmpty(errorMessage).toLowerCase(Locale.ROOT);
        if (normalized.contains("timeout") || normalized.contains("connection") || normalized.contains("network")) {
            return "DEPENDENCY_ERROR";
        }
        return "TOOL_ERROR";
    }

    private String toErrorLabel(String type) {
        return switch (type) {
            case "PERMISSION_DENIED" -> "权限拒绝";
            case "MODEL_ERROR" -> "模型异常";
            case "TOOL_ERROR" -> "工具异常";
            case "DEPENDENCY_ERROR" -> "外部依赖异常";
            default -> type;
        };
    }

    private long countFailedAuditLogs(List<AiAuditLogEntity> logs) {
        return logs.stream().filter(item -> Boolean.FALSE.equals(item.getSuccess())).count();
    }

    private long countFailedToolAudits(List<AiToolAuditLogEntity> logs) {
        return logs.stream().filter(item -> Boolean.FALSE.equals(item.getSuccess())).count();
    }

    private long averageAuditLatency(List<AiAuditLogEntity> logs) {
        return logs.isEmpty()
                ? 0L
                : Math.round(logs.stream().mapToLong(item -> item.getLatencyMs() == null ? 0L : item.getLatencyMs()).average().orElse(0d));
    }

    private long averageToolLatency(List<AiToolAuditLogEntity> logs) {
        return logs.isEmpty()
                ? 0L
                : Math.round(logs.stream().mapToLong(item -> item.getLatencyMs() == null ? 0L : item.getLatencyMs()).average().orElse(0d));
    }

    private String firstNonBlank(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left;
        }
        return right;
    }

    private String nullToEmpty(String value) {
        return value == null ? EMPTY_VALUE : value;
    }
}
