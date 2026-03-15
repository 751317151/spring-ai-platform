package com.huah.ai.platform.agent.tools;

import com.huah.ai.platform.agent.config.ToolsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 天气查询工具集 — 接入和风天气 (QWeather) API
 * <p>
 * 两步调用：城市名 → GEO API 查 locationId → Weather API 查天气
 */
@Slf4j
@Component
public class WeatherTools {

    private final RestClient weatherClient;
    private final RestClient geoClient;
    private final ToolsProperties.QWeatherConfig config;

    public WeatherTools(RestClient.Builder restClientBuilder, ToolsProperties props) {
        this.config = props.getWeather().getQweather();
        this.weatherClient = restClientBuilder.clone()
                .baseUrl(config.getBaseUrl())
                .build();
        this.geoClient = restClientBuilder.clone()
                .baseUrl(config.getBaseUrl())
                .build();
    }

    @Tool(description = "查询指定城市的当前天气，包括温度、湿度、风力、天气描述")
    public Map<String, Object> queryWeather(
            @ToolParam(description = "城市名称，如北京、上海、深圳") String city) {
        log.info("[Tool] queryWeather: city={}", city);
        try {
            if (config.getApiKey() == null || config.getApiKey().isBlank()) {
                return Map.of("error", "天气服务未配置 API Key，请设置环境变量 QWEATHER_API_KEY");
            }

            String locationId = lookupCityId(city);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = weatherClient.get()
                    .uri("/v7/weather/now?location={loc}&key={key}", locationId, config.getApiKey())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !"200".equals(String.valueOf(response.get("code")))) {
                return Map.of("error", "天气查询失败，状态码: " + (response != null ? response.get("code") : "null"));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> now = (Map<String, Object>) response.get("now");
            Map<String, Object> result = new HashMap<>();
            result.put("city", city);
            result.put("temperature", now.get("temp") + "°C");
            result.put("feelsLike", now.get("feelsLike") + "°C");
            result.put("humidity", now.get("humidity") + "%");
            result.put("wind", now.get("windDir") + " " + now.get("windScale") + "级");
            result.put("weather", String.valueOf(now.get("text")));
            result.put("updateTime", String.valueOf(now.get("obsTime")));
            return result;
        } catch (Exception e) {
            log.error("[Tool] queryWeather failed: city={}, error={}", city, e.getMessage());
            return Map.of("error", "天气查询失败: " + e.getMessage());
        }
    }

    @Tool(description = "查询指定城市的多日天气预报")
    public Object queryWeatherForecast(
            @ToolParam(description = "城市名称") String city,
            @ToolParam(description = "预报天数，支持 3 或 7") int days) {
        log.info("[Tool] queryWeatherForecast: city={}, days={}", city, days);
        try {
            if (config.getApiKey() == null || config.getApiKey().isBlank()) {
                return List.of(Map.of("error", "天气服务未配置 API Key，请设置环境变量 QWEATHER_API_KEY"));
            }

            int forecastDays = (days <= 3) ? 3 : 7;
            String locationId = lookupCityId(city);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = weatherClient.get()
                    .uri("/v7/weather/{days}d?location={loc}&key={key}", forecastDays, locationId, config.getApiKey())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !"200".equals(String.valueOf(response.get("code")))) {
                return List.of(Map.of("error", "天气预报查询失败"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> daily = (List<Map<String, Object>>) response.get("daily");
            if (daily == null) {
                return List.of(Map.of("error", "无预报数据"));
            }

            return daily.stream()
                    .limit(days)
                    .map(d -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("date", String.valueOf(d.get("fxDate")));
                        item.put("weather", String.valueOf(d.get("textDay")));
                        item.put("high", d.get("tempMax") + "°C");
                        item.put("low", d.get("tempMin") + "°C");
                        item.put("wind", d.get("windDirDay") + " " + d.get("windScaleDay") + "级");
                        return item;
                    })
                    .toList();
        } catch (Exception e) {
            log.error("[Tool] queryWeatherForecast failed: city={}, error={}", city, e.getMessage());
            return List.of(Map.of("error", "天气预报查询失败: " + e.getMessage()));
        }
    }

    /**
     * 城市名称 → locationId（和风天气 GEO API）
     */
    @SuppressWarnings("unchecked")
    private String lookupCityId(String city) {
        Map<String, Object> response = geoClient.get()
                .uri("/geo/v2/city/lookup?location={city}&key={key}&number=1", city, config.getApiKey())
                .retrieve()
                .body(Map.class);

        if (response == null || !"200".equals(String.valueOf(response.get("code")))) {
            throw new RuntimeException("城市查询失败: " + (response != null ? response.get("code") : "null"));
        }

        List<Map<String, Object>> locations = (List<Map<String, Object>>) response.get("location");
        if (locations == null || locations.isEmpty()) {
            throw new RuntimeException("未找到城市: " + city);
        }

        return locations.get(0).get("id").toString();
    }
}
