package com.huah.ai.platform.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 网络搜索工具集
 */
@Slf4j
@Component
public class SearchTools {

    @Tool(description = "搜索互联网，返回与查询相关的搜索结果列表")
    public List<Map<String, String>> webSearch(
            @ToolParam(description = "搜索关键词") String query,
            @ToolParam(description = "最大返回结果数，默认5") int maxResults) {
        log.info("[Tool] webSearch: query={}, maxResults={}", query, maxResults);
        return List.of(
                Map.of("title", "Spring AI 官方文档", "url", "https://docs.spring.io/spring-ai", "snippet", "Spring AI 提供了与各种 AI 模型集成的能力..."),
                Map.of("title", "Spring Boot 3.x 新特性", "url", "https://spring.io/blog/spring-boot-3", "snippet", "Spring Boot 3.x 引入了对 GraalVM 原生镜像的支持..."),
                Map.of("title", "MCP 协议详解", "url", "https://modelcontextprotocol.io", "snippet", "Model Context Protocol 是一种开放标准，用于连接 AI 与外部工具...")
        );
    }

    @Tool(description = "获取指定网页的内容摘要")
    public Map<String, String> summarizeUrl(
            @ToolParam(description = "要摘要的网页 URL") String url) {
        log.info("[Tool] summarizeUrl: url={}", url);
        return Map.of(
                "url", url,
                "title", "网页标题示例",
                "summary", "这是一篇关于人工智能在企业应用中的最新实践文章。文章涵盖了 AI Agent 架构设计、工具调用链、记忆管理等核心主题，并提供了多个实际案例分析。",
                "wordCount", "约 3500 字"
        );
    }
}
