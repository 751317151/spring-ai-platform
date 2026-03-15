package com.huah.ai.platform.agent.tools;

import com.huah.ai.platform.agent.config.ToolsProperties;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网络搜索工具集 — Tavily (主) + Google Custom Search (备) + Jsoup 网页抓取
 */
@Slf4j
@Component
public class SearchTools {

    private final RestClient tavilyClient;
    private final ToolsProperties.SearchConfig config;

    public SearchTools(RestClient.Builder restClientBuilder, ToolsProperties props) {
        this.config = props.getSearch();
        this.tavilyClient = restClientBuilder.clone()
                .baseUrl(config.getTavily().getBaseUrl())
                .build();
    }

    @Tool(description = "搜索互联网，返回与查询相关的搜索结果列表")
    public List<Map<String, String>> webSearch(
            @ToolParam(description = "搜索关键词") String query,
            @ToolParam(description = "最大返回结果数，默认5") int maxResults) {
        log.info("[Tool] webSearch: query={}, maxResults={}", query, maxResults);
        int limit = Math.max(1, Math.min(maxResults, 10));

        // Try Tavily first
        String tavilyKey = config.getTavily().getApiKey();
        if (tavilyKey != null && !tavilyKey.isBlank()) {
            try {
                return searchViaTavily(query, limit);
            } catch (Exception e) {
                log.warn("[Tool] Tavily search failed, trying Google fallback: {}", e.getMessage());
            }
        }

        // Fallback to Google Custom Search
        String googleKey = config.getGoogle().getApiKey();
        String googleCx = config.getGoogle().getCx();
        if (googleKey != null && !googleKey.isBlank() && googleCx != null && !googleCx.isBlank()) {
            try {
                return searchViaGoogle(query, limit);
            } catch (Exception e) {
                log.error("[Tool] Google search also failed: {}", e.getMessage());
            }
        }

        return List.of(Map.of("error", "搜索服务暂不可用，请配置 TAVILY_API_KEY 或 GOOGLE_SEARCH_API_KEY 环境变量"));
    }

    @Tool(description = "获取指定网页的文本内容，用于分析和总结")
    public Map<String, String> summarizeUrl(
            @ToolParam(description = "要获取的网页 URL") String url) {
        log.info("[Tool] summarizeUrl: url={}", url);
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; SpringAIBot/1.0)")
                    .timeout(config.getSummarize().getTimeoutMs())
                    .followRedirects(true)
                    .maxBodySize(config.getSummarize().getMaxContentLength() * 2)
                    .get();

            String title = doc.title();

            // Remove non-content elements
            doc.select("script, style, nav, header, footer, aside, iframe, noscript").remove();
            String bodyText = doc.body() != null ? doc.body().text() : "";

            int maxLen = config.getSummarize().getMaxContentLength();
            if (bodyText.length() > maxLen) {
                bodyText = bodyText.substring(0, maxLen) + "...(内容已截断)";
            }

            Map<String, String> result = new HashMap<>();
            result.put("url", url);
            result.put("title", title != null ? title : "");
            result.put("content", bodyText);
            result.put("charCount", String.valueOf(bodyText.length()));
            return result;
        } catch (Exception e) {
            log.error("[Tool] summarizeUrl failed: url={}, error={}", url, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("url", url);
            error.put("error", "无法获取网页内容: " + e.getMessage());
            return error;
        }
    }

    // ===== Tavily Search =====

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> searchViaTavily(String query, int limit) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("api_key", config.getTavily().getApiKey());
        requestBody.put("query", query);
        requestBody.put("max_results", limit);
        requestBody.put("search_depth", "basic");
        requestBody.put("include_answer", false);

        Map<String, Object> response = tavilyClient.post()
                .uri("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("results")) {
            throw new RuntimeException("Tavily 返回结果为空");
        }

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        List<Map<String, String>> output = new ArrayList<>();
        for (Map<String, Object> r : results) {
            Map<String, String> item = new HashMap<>();
            item.put("title", String.valueOf(r.getOrDefault("title", "")));
            item.put("url", String.valueOf(r.getOrDefault("url", "")));
            item.put("snippet", String.valueOf(r.getOrDefault("content", "")));
            output.add(item);
        }
        log.info("[Tool] Tavily search returned {} results for query: {}", output.size(), query);
        return output;
    }

    // ===== Google Custom Search =====

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> searchViaGoogle(String query, int limit) {
        RestClient googleClient = RestClient.builder()
                .baseUrl("https://www.googleapis.com")
                .build();

        Map<String, Object> response = googleClient.get()
                .uri("/customsearch/v1?key={key}&cx={cx}&q={q}&num={num}",
                        config.getGoogle().getApiKey(),
                        config.getGoogle().getCx(),
                        query,
                        limit)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("items")) {
            throw new RuntimeException("Google Search 返回结果为空");
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
        List<Map<String, String>> output = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Map<String, String> mapped = new HashMap<>();
            mapped.put("title", String.valueOf(item.getOrDefault("title", "")));
            mapped.put("url", String.valueOf(item.getOrDefault("link", "")));
            mapped.put("snippet", String.valueOf(item.getOrDefault("snippet", "")));
            output.add(mapped);
        }
        log.info("[Tool] Google search returned {} results for query: {}", output.size(), query);
        return output;
    }
}
