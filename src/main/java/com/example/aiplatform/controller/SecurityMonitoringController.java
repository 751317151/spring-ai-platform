package com.example.aiplatform.controller;

import com.example.aiplatform.dto.MonitoringSnapshot;
import com.example.aiplatform.monitoring.MonitoringService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class SecurityMonitoringController {

    private final MonitoringService monitoringService;

    public SecurityMonitoringController(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/monitoring")
    public MonitoringSnapshot monitoringSnapshot() {
        return monitoringService.snapshot();
    }
}
