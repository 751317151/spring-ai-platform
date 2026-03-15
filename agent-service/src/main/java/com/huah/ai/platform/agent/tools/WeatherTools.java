package com.huah.ai.platform.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 天气查询工具集
 */
@Slf4j
@Component
public class WeatherTools {

    @Tool(description = "查询指定城市的当前天气，包括温度、湿度、风力、天气描述")
    public Map<String, Object> queryWeather(
            @ToolParam(description = "城市名称，如北京、上海、深圳") String city) {
        log.info("[Tool] queryWeather: city={}", city);
        return Map.of(
                "city", city,
                "temperature", "26°C",
                "humidity", "65%",
                "wind", "东南风 3级",
                "weather", "多云",
                "aqi", 72,
                "aqiLevel", "良",
                "updateTime", LocalDate.now().toString()
        );
    }

    @Tool(description = "查询指定城市的多日天气预报")
    public List<Map<String, Object>> queryWeatherForecast(
            @ToolParam(description = "城市名称") String city,
            @ToolParam(description = "预报天数，1-7") int days) {
        log.info("[Tool] queryWeatherForecast: city={}, days={}", city, days);
        return List.of(
                Map.of("date", LocalDate.now().plusDays(1).toString(), "weather", "晴", "high", "28°C", "low", "18°C", "wind", "东风 2级"),
                Map.of("date", LocalDate.now().plusDays(2).toString(), "weather", "多云", "high", "25°C", "low", "16°C", "wind", "东南风 3级"),
                Map.of("date", LocalDate.now().plusDays(3).toString(), "weather", "小雨", "high", "22°C", "low", "15°C", "wind", "北风 4级")
        );
    }
}
