package com.huah.ai.platform.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "tools")
public class ToolsProperties {

    private SearchConfig search = new SearchConfig();
    private WeatherConfig weather = new WeatherConfig();
    private DataAnalysisConfig dataAnalysis = new DataAnalysisConfig();
    private InternalApiConfig internalApi = new InternalApiConfig();
    private SecurityConfig security = new SecurityConfig();

    @Data
    public static class SearchConfig {
        private TavilyConfig tavily = new TavilyConfig();
        private GoogleConfig google = new GoogleConfig();
        private SummarizeConfig summarize = new SummarizeConfig();
    }

    @Data
    public static class TavilyConfig {
        private String apiKey;
        private String baseUrl = "https://api.tavily.com";
    }

    @Data
    public static class GoogleConfig {
        private String apiKey;
        private String cx;
    }

    @Data
    public static class SummarizeConfig {
        private int timeoutMs = 15000;
        private int maxContentLength = 5000;
    }

    @Data
    public static class WeatherConfig {
        private QWeatherConfig qweather = new QWeatherConfig();
    }

    @Data
    public static class QWeatherConfig {
        private String apiKey;
        private String baseUrl = "https://devapi.qweather.com";
        private String geoBaseUrl = "https://geoapi.qweather.com";
    }

    @Data
    public static class DataAnalysisConfig {
        private int queryTimeoutSeconds = 30;
        private int maxRows = 1000;
        private String defaultSchema = "public";
        private java.util.List<String> allowedTables = new java.util.ArrayList<>();
        private java.util.List<String> blockedTables = new java.util.ArrayList<>();
    }

    @Data
    public static class InternalApiConfig {
        private java.util.Map<String, ConnectorDefinition> connectors = new java.util.LinkedHashMap<>();
    }

    @Data
    public static class SecurityConfig {
        private boolean enabled = false;
        private java.util.Map<String, java.util.List<String>> agentToolAllowlist = new java.util.LinkedHashMap<>();
        private java.util.Map<String, java.util.List<String>> agentConnectorAllowlist = new java.util.LinkedHashMap<>();
        private java.util.Map<String, java.util.List<String>> agentMcpServerAllowlist = new java.util.LinkedHashMap<>();
        private java.util.Map<String, java.util.Map<String, java.util.List<String>>> agentConnectorResourceAllowlist = new java.util.LinkedHashMap<>();
        private java.util.Map<String, java.util.Map<String, java.util.List<String>>> agentMcpToolAllowlist = new java.util.LinkedHashMap<>();
        private java.util.Map<String, java.util.List<String>> agentDataScopeAllowlist = new java.util.LinkedHashMap<>();
        private java.util.Map<String, java.util.List<String>> agentDataSourceAllowlist = new java.util.LinkedHashMap<>();
        private java.util.Map<String, java.util.List<String>> agentCrossSchemaAccessAllowlist = new java.util.LinkedHashMap<>();
        private java.util.Map<String, String> schemaDataSourceBindings = new java.util.LinkedHashMap<>();
        private java.util.Map<String, Integer> agentMaxConcurrency = new java.util.LinkedHashMap<>();
        private java.util.Map<String, Integer> agentMaxQueueDepth = new java.util.LinkedHashMap<>();
        private java.util.Map<String, Long> agentQueueWaitTimeoutMs = new java.util.LinkedHashMap<>();
        private java.util.Map<String, Long> agentRequestTimeoutMs = new java.util.LinkedHashMap<>();
        private java.util.Map<String, Long> agentStreamTimeoutMs = new java.util.LinkedHashMap<>();
    }

    @Data
    public static class ConnectorDefinition {
        private boolean enabled = false;
        private String name;
        private String baseUrl;
        private String authHeaderName;
        private String authHeaderValue;
        private int timeoutMs = 5000;
        private java.util.List<String> allowedPathPrefixes = new java.util.ArrayList<>();
    }
}
