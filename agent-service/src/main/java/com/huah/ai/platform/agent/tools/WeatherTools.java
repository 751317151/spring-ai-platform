package com.huah.ai.platform.agent.tools;

import com.huah.ai.platform.agent.config.ToolsProperties;
import com.huah.ai.platform.common.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Weather tools backed by the QWeather API.
 */
@Slf4j
@Component
public class WeatherTools {

    private static final String FIELD_ERROR = "error";
    private static final String RESPONSE_CODE_OK = "200";
    private static final String FIELD_CITY = "city";
    private static final String FIELD_DATE = "date";
    private static final String FIELD_WEATHER = "weather";
    private static final String FIELD_WIND = "wind";
    private static final String UNIT_CELSIUS = " C";
    private static final String UNIT_WIND_LEVEL = " level";
    private static final String MESSAGE_API_KEY_MISSING =
            "Weather service is unavailable. Configure QWEATHER_API_KEY first.";
    private static final String MESSAGE_CURRENT_WEATHER_FAILED = "Failed to query current weather";
    private static final String MESSAGE_FORECAST_FAILED = "Failed to query weather forecast";
    private static final String MESSAGE_FORECAST_EMPTY = "No forecast data returned";
    private static final String MESSAGE_CITY_LOOKUP_FAILED = "Failed to resolve city";
    private static final String MESSAGE_CITY_NOT_FOUND = "City not found: ";

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

    @Tool(description = "Query the current weather for a city, including temperature, humidity, wind, and conditions.")
    public Map<String, Object> queryWeather(
            @ToolParam(description = "City name, for example Beijing or Shanghai") String city) {
        log.info("[Tool] queryWeather: city={}", city);
        try {
            if (!hasApiKey()) {
                return Map.of(FIELD_ERROR, MESSAGE_API_KEY_MISSING);
            }

            String locationId = lookupCityId(city);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = weatherClient.get()
                    .uri("/v7/weather/now?location={loc}&key={key}", locationId, config.getApiKey())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !RESPONSE_CODE_OK.equals(String.valueOf(response.get("code")))) {
                return Map.of(FIELD_ERROR, MESSAGE_CURRENT_WEATHER_FAILED + ": " + resolveResponseCode(response));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> now = (Map<String, Object>) response.get("now");
            Map<String, Object> result = new HashMap<>();
            result.put(FIELD_CITY, city);
            result.put("temperature", now.get("temp") + UNIT_CELSIUS);
            result.put("feelsLike", now.get("feelsLike") + UNIT_CELSIUS);
            result.put("humidity", now.get("humidity") + "%");
            result.put(FIELD_WIND, now.get("windDir") + " " + now.get("windScale") + UNIT_WIND_LEVEL);
            result.put(FIELD_WEATHER, String.valueOf(now.get("text")));
            result.put("updateTime", String.valueOf(now.get("obsTime")));
            return result;
        } catch (AiServiceException | RestClientException | IllegalArgumentException exception) {
            log.error("[Tool] queryWeather failed: city={}, error={}", city, exception.getMessage(), exception);
            return Map.of(FIELD_ERROR, MESSAGE_CURRENT_WEATHER_FAILED + ": " + exception.getMessage());
        }
    }

    @Tool(description = "Query a 3-day or 7-day weather forecast for a city.")
    public Object queryWeatherForecast(
            @ToolParam(description = "City name") String city,
            @ToolParam(description = "Forecast days, supports 3 or 7") int days) {
        log.info("[Tool] queryWeatherForecast: city={}, days={}", city, days);
        try {
            if (!hasApiKey()) {
                return List.of(Map.of(FIELD_ERROR, MESSAGE_API_KEY_MISSING));
            }

            int forecastDays = days <= 3 ? 3 : 7;
            String locationId = lookupCityId(city);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = weatherClient.get()
                    .uri("/v7/weather/{days}d?location={loc}&key={key}", forecastDays, locationId, config.getApiKey())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !RESPONSE_CODE_OK.equals(String.valueOf(response.get("code")))) {
                return List.of(Map.of(FIELD_ERROR, MESSAGE_FORECAST_FAILED + ": " + resolveResponseCode(response)));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> daily = (List<Map<String, Object>>) response.get("daily");
            if (daily == null) {
                return List.of(Map.of(FIELD_ERROR, MESSAGE_FORECAST_EMPTY));
            }

            return daily.stream()
                    .limit(days)
                    .map(item -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put(FIELD_DATE, String.valueOf(item.get("fxDate")));
                        result.put(FIELD_WEATHER, String.valueOf(item.get("textDay")));
                        result.put("high", item.get("tempMax") + UNIT_CELSIUS);
                        result.put("low", item.get("tempMin") + UNIT_CELSIUS);
                        result.put(FIELD_WIND, item.get("windDirDay") + " " + item.get("windScaleDay") + UNIT_WIND_LEVEL);
                        return result;
                    })
                    .toList();
        } catch (AiServiceException | RestClientException | IllegalArgumentException exception) {
            log.error("[Tool] queryWeatherForecast failed: city={}, error={}", city, exception.getMessage(), exception);
            return List.of(Map.of(FIELD_ERROR, MESSAGE_FORECAST_FAILED + ": " + exception.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private String lookupCityId(String city) {
        Map<String, Object> response = geoClient.get()
                .uri("/geo/v2/city/lookup?location={city}&key={key}&number=1", city, config.getApiKey())
                .retrieve()
                .body(Map.class);

        if (response == null || !RESPONSE_CODE_OK.equals(String.valueOf(response.get("code")))) {
            throw new AiServiceException(MESSAGE_CITY_LOOKUP_FAILED + ": " + resolveResponseCode(response));
        }

        List<Map<String, Object>> locations = (List<Map<String, Object>>) response.get("location");
        if (locations == null || locations.isEmpty()) {
            throw new AiServiceException(MESSAGE_CITY_NOT_FOUND + city);
        }

        return String.valueOf(locations.get(0).get("id"));
    }

    private boolean hasApiKey() {
        return config.getApiKey() != null && !config.getApiKey().isBlank();
    }

    private String resolveResponseCode(Map<String, Object> response) {
        return response == null ? "null" : String.valueOf(response.get("code"));
    }
}
