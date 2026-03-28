package com.huah.ai.platform.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent.lifecycle")
public class AgentLifecycleProperties {
    private AuditRetention audit = new AuditRetention();
    private ToolAuditRetention toolAudit = new ToolAuditRetention();
    private TraceRetention trace = new TraceRetention();
    private Automation automation = new Automation();
    private Archive archive = new Archive();

    @Data
    public static class AuditRetention {
        private int archiveAfterDays = 14;
        private int deleteAfterDays = 60;
    }

    @Data
    public static class ToolAuditRetention {
        private int archiveAfterDays = 14;
        private int deleteAfterDays = 45;
    }

    @Data
    public static class TraceRetention {
        private int archiveAfterDays = 7;
        private int deleteAfterDays = 30;
    }

    @Data
    public static class Automation {
        private boolean enabled = false;
        private boolean dryRun = true;
        private long fixedDelayMs = 3600000L;
        private long initialDelayMs = 120000L;
    }

    @Data
    public static class Archive {
        private boolean enabled = true;
        private String manifestDir = "data/agent-lifecycle-archive";
        private int sampleLimit = 5;
        private int exportBatchSize = 200;
    }
}
