package com.huah.ai.platform.monitor.alert;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "monitor.alerts")
public class AlertRuleProperties {

    private List<Rule> rules = new ArrayList<>();

    @Data
    public static class Rule {
        private String id;
        private String level;
        private String type;
        private String summary;
        private String metric;
        private double threshold;
    }
}
