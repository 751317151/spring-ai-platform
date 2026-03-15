package com.huah.ai.platform.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 生产质控领域工具集
 */
@Slf4j
@Component
public class QcTools {

    @Tool(description = "查询生产质控数据，获取指定时间段内的不良品率和质检报告")
    public Map<String, Object> queryQualityControl(
            @ToolParam(description = "生产线编号") String lineId,
            @ToolParam(description = "开始日期，格式 yyyy-MM-dd") String startDate,
            @ToolParam(description = "结束日期，格式 yyyy-MM-dd") String endDate) {
        log.info("[Tool] queryQC: line={}, start={}, end={}", lineId, startDate, endDate);
        return Map.of(
                "lineId", lineId,
                "period", startDate + " ~ " + endDate,
                "totalProduced", 10000,
                "defects", 120,
                "defectRate", "1.2%",
                "topDefects", List.of(
                        Map.of("type", "外观瑕疵", "count", 60, "ratio", "50%"),
                        Map.of("type", "尺寸偏差", "count", 40, "ratio", "33%")
                )
        );
    }
}
