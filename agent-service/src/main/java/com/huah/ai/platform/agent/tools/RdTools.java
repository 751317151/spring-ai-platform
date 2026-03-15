package com.huah.ai.platform.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 研发领域工具集
 */
@Slf4j
@Component
public class RdTools {

    @Tool(description = "查询 Jira 缺陷系统，获取指定项目的 Bug 列表或特定 Issue 详情")
    public Map<String, Object> queryJira(
            @ToolParam(description = "Jira 项目 Key，如 PROJ-123 或 PROJ") String projectOrIssue,
            @ToolParam(description = "过滤状态: TODO|IN_PROGRESS|DONE，可为空") String status) {
        log.info("[Tool] queryJira: project={}, status={}", projectOrIssue, status);
        return Map.of(
                "issues", List.of(
                        Map.of("key", "PROJ-101", "summary", "登录接口性能问题", "status", "IN_PROGRESS", "priority", "High"),
                        Map.of("key", "PROJ-102", "summary", "Redis 连接池泄漏", "status", "TODO", "priority", "Critical")
                ),
                "total", 2
        );
    }

    @Tool(description = "查询 Confluence 技术文档，按关键词搜索相关文档")
    public List<Map<String, String>> queryConfluence(
            @ToolParam(description = "搜索关键词") String keyword,
            @ToolParam(description = "文档空间 Key，如 TECH、ARCH，可为空") String spaceKey) {
        log.info("[Tool] queryConfluence: keyword={}, space={}", keyword, spaceKey);
        return List.of(
                Map.of("title", "微服务架构设计规范", "url", "http://confluence/TECH/arch-spec", "space", "TECH"),
                Map.of("title", "数据库设计规范", "url", "http://confluence/TECH/db-spec", "space", "TECH")
        );
    }

    @Tool(description = "查询 SonarQube 代码质量报告，获取指定项目的质量指标")
    public Map<String, Object> querySonar(
            @ToolParam(description = "SonarQube 项目 Key") String projectKey) {
        log.info("[Tool] querySonar: project={}", projectKey);
        return Map.of(
                "projectKey", projectKey,
                "bugs", 3,
                "vulnerabilities", 1,
                "codeSmells", 45,
                "coverage", "72.5%",
                "duplications", "8.3%",
                "qualityGate", "WARN"
        );
    }
}
