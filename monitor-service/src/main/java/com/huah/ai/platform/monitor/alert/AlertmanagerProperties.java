package com.huah.ai.platform.monitor.alert;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "monitor.alertmanager")
public class AlertmanagerProperties {
    private boolean enabled = false;
    private String baseUrl = "http://localhost:9093";
    private String apiPath = "/api/v2/alerts";
    private String uiBaseUrl = "http://localhost:9093";
    private String silencePath = "/#/silences/new?filter=";
    private int timeoutMs = 3000;
}
