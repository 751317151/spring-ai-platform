package com.example.aiplatform.controller;

import com.example.aiplatform.dto.AgentTaskRequest;
import com.example.aiplatform.agent.AgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/task")
    public Map<String, String> execute(@Valid @RequestBody AgentTaskRequest request) {
        return Map.of("result", agentService.execute(request));
    }

    @GetMapping("/collaboration")
    public Map<String, String> collaboration(@RequestParam String scenario) {
        return Map.of("plan", agentService.collaborate(scenario));
    }
}
