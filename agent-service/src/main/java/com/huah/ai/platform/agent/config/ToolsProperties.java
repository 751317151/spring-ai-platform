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
    }
}
