package com.example.aiplatform.agent;

import com.example.aiplatform.dto.AgentTaskRequest;
import com.example.aiplatform.model.AgentMemory;
import org.springframework.stereotype.Service;

@Service
public class AgentService {

    private final MemoryService memoryService;
    private final ToolOrchestrationService toolOrchestrationService;

    public AgentService(MemoryService memoryService, ToolOrchestrationService toolOrchestrationService) {
        this.memoryService = memoryService;
        this.toolOrchestrationService = toolOrchestrationService;
    }

    public String execute(AgentTaskRequest request) {
        AgentMemory memory = memoryService.load(request.userId());
        String toolResult = toolOrchestrationService.invokeBusinessTools(request.task(), request.context());

        String result = "Agent=" + request.agentType() + "\n"
                + "短期记忆=" + memory.shortTermSummary() + "\n"
                + "长期画像=" + memory.longTermProfile() + "\n"
                + "工具调用结果=" + toolResult + "\n"
                + "任务输出：建议执行\"" + request.task() + "\"的后续流程自动化。";

        memoryService.update(
                request.userId(),
                "最近任务: " + request.task(),
                memory.longTermProfile().equals("无长期画像") ? "偏好：自动化与效率提升" : memory.longTermProfile()
        );
        return result;
    }

    public String collaborate(String scenario) {
        return "Multi-Agent 协作编排已启动：\n"
                + "1) 需求解析 Agent\n"
                + "2) 数据检索 Agent\n"
                + "3) 合规审查 Agent\n"
                + "4) 执行决策 Agent\n"
                + "场景=" + scenario;
    }
}
