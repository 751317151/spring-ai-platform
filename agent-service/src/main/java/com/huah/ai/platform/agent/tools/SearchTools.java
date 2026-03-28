package com.huah.ai.platform.agent.tools;

import com.huah.ai.platform.agent.config.ToolsProperties;
import com.huah.ai.platform.common.exception.AiServiceException;
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
 * Network search tools backed by Tavily, Google Custom Search, and Jsoup page extraction.
 */
@Slf4j
@Component
public class SearchTools {

    private static final String FIELD_ERROR = "error";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_URL = "url";
    private static final String FIELD_SNIPPET = "snippet";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_CHAR_COUNT = "charCount";
    private static final String RESPONSE_RESULTS = "results";
    private static final String RESPONSE_ITEMS = "items";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; SpringAIBot/1.0)";
    private static final String MESSAGE_SEARCH_SERVICE_UNAVAILABLE =
            "Search service is unavailable. Configure TAVILY_API_KEY or GOOGLE_SEARCH_API_KEY.";
    private static final String MESSAGE_SUMMARY_TRUNCATED_SUFFIX = "...(content truncated)";
    private static final String MESSAGE_FETCH_URL_FAILED = "Failed to fetch page content: ";
    private static final String MESSAGE_TAVILY_EMPTY_RESULT = "Tavily returned an empty result set";
    private static final String MESSAGE_GOOGLE_EMPTY_RESULT = "Google Custom Search returned an empty result set";

    private final RestClient tavilyClient;
    private final ToolsProperties.SearchConfig config;

    public SearchTools(RestClient.Builder restClientBuilder, ToolsProperties props) {
        this.config = props.getSearch();
        this.tavilyClient = restClientBuilder.clone()
                .baseUrl(config.getTavily().getBaseUrl())
                .build();
    }

    @Tool(description = "Search the internet and return relevant search results.")
    public List<Map<String, String>> webSearch(
            @ToolParam(description = "Search query") String query,
            @ToolParam(description = "Maximum number of results, default up to 10") int maxResults) {
        log.info("[Tool] webSearch: query={}, maxResults={}", query, maxResults);
        int limit = Math.max(1, Math.min(maxResults, 10));

        String tavilyKey = config.getTavily().getApiKey();
        if (hasText(tavilyKey)) {
            try {
                return searchViaTavily(query, limit);
            } catch (Exception exception) {
                log.warn("[Tool] Tavily search failed, trying Google fallback: {}", exception.getMessage());
            }
        }

        String googleKey = config.getGoogle().getApiKey();
        String googleCx = config.getGoogle().getCx();
        if (hasText(googleKey) && hasText(googleCx)) {
            try {
                return searchViaGoogle(query, limit);
            } catch (Exception exception) {
                log.error("[Tool] Google search also failed: {}", exception.getMessage());
            }
        }

        return List.of(Map.of(FIELD_ERROR, MESSAGE_SEARCH_SERVICE_UNAVAILABLE));
    }

    @Tool(description = "Fetch and summarize readable content from a specific URL.")
    public Map<String, String> summarizeUrl(
            @ToolParam(description = "Target page URL") String url) {
        log.info("[Tool] summarizeUrl: url={}", url);
        try {
            Document document = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(config.getSummarize().getTimeoutMs())
                    .followRedirects(true)
                    .maxBodySize(config.getSummarize().getMaxContentLength() * 2)
                    .get();

            document.select("script, style, nav, header, footer, aside, iframe, noscript").remove();
            String bodyText = document.body() != null ? document.body().text() : "";
            bodyText = truncateContent(bodyText, config.getSummarize().getMaxContentLength());

            Map<String, String> result = new HashMap<>();
            result.put(FIELD_URL, url);
            result.put(FIELD_TITLE, document.title() == null ? "" : document.title());
            result.put(FIELD_CONTENT, bodyText);
            result.put(FIELD_CHAR_COUNT, String.valueOf(bodyText.length()));
            return result;
        } catch (Exception exception) {
            log.error("[Tool] summarizeUrl failed: url={}, error={}", url, exception.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put(FIELD_URL, url);
            error.put(FIELD_ERROR, MESSAGE_FETCH_URL_FAILED + exception.getMessage());
            return error;
        }
    }

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

        if (response == null || !response.containsKey(RESPONSE_RESULTS)) {
            throw new AiServiceException(MESSAGE_TAVILY_EMPTY_RESULT);
        }

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get(RESPONSE_RESULTS);
        List<Map<String, String>> output = new ArrayList<>();
        for (Map<String, Object> result : results) {
            Map<String, String> item = new HashMap<>();
            item.put(FIELD_TITLE, String.valueOf(result.getOrDefault(FIELD_TITLE, "")));
            item.put(FIELD_URL, String.valueOf(result.getOrDefault(FIELD_URL, "")));
            item.put(FIELD_SNIPPET, String.valueOf(result.getOrDefault(FIELD_CONTENT, "")));
            output.add(item);
        }
        log.info("[Tool] Tavily search returned {} results for query: {}", output.size(), query);
        return output;
    }

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

        if (response == null || !response.containsKey(RESPONSE_ITEMS)) {
            throw new AiServiceException(MESSAGE_GOOGLE_EMPTY_RESULT);
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get(RESPONSE_ITEMS);
        List<Map<String, String>> output = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Map<String, String> mapped = new HashMap<>();
            mapped.put(FIELD_TITLE, String.valueOf(item.getOrDefault(FIELD_TITLE, "")));
            mapped.put(FIELD_URL, String.valueOf(item.getOrDefault("link", "")));
            mapped.put(FIELD_SNIPPET, String.valueOf(item.getOrDefault(FIELD_SNIPPET, "")));
            output.add(mapped);
        }
        log.info("[Tool] Google search returned {} results for query: {}", output.size(), query);
        return output;
    }

    private String truncateContent(String content, int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + MESSAGE_SUMMARY_TRUNCATED_SUFFIX;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
