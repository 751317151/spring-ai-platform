package com.huah.ai.platform.agent.multi;

import com.huah.ai.platform.agent.audit.ToolExecutionContext;
import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import com.huah.ai.platform.agent.tools.CodeTools;
import com.huah.ai.platform.agent.tools.DataAnalysisTools;
import com.huah.ai.platform.agent.tools.SearchTools;
import com.huah.ai.platform.agent.tools.WeatherTools;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentOrchestrator {

    private static final String PLANNER_SYSTEM = """
            你是任务规划专家。请将复杂请求拆解为可执行步骤，明确每一步目标、依赖和建议工具。
            输出格式：
            ## 任务分析
            ## 执行计划
            1. 子任务 - 建议工具
            2. 子任务 - 建议工具
            ## 预期结果
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final ConversationMemoryService memoryService;
    private final SearchTools searchTools;
    private final WeatherTools weatherTools;
    private final DataAnalysisTools dataAnalysisTools;
    private final CodeTools codeTools;

    public StepResult executeComplexTask(String userId, String sessionId, String task) {
        log.info("Multi-Agent 任务开始, userId={}, task={}", userId, task);
        String internalId = sessionId + "-multi-" + System.currentTimeMillis();

        StepResult planResult = planTask(task, internalId + "-planner");
        StepResult execResult = executeWithTools(userId, task, planResult.content, internalId + "-executor");
        StepResult criticResult = critique(task, execResult.content, internalId + "-critic");

        log.info("Multi-Agent 任务完成, totalTokens={}/{}",
                planResult.promptTokens + execResult.promptTokens + criticResult.promptTokens,
                planResult.completionTokens + execResult.completionTokens + criticResult.completionTokens);

        return new StepResult(
                criticResult.content,
                planResult.promptTokens + execResult.promptTokens + criticResult.promptTokens,
                planResult.completionTokens + execResult.completionTokens + criticResult.completionTokens
        );
    }

    public StepResult planTask(String task, String sessionId) {
        ChatResponse response = chatClientBuilder.clone()
                .build()
                .prompt()
                .system(PLANNER_SYSTEM)
                .user(task)
                .advisors(a -> a.param(CONVERSATION_ID, sessionId))
                .call()
                .chatResponse();
        return StepResult.from(response);
    }

    public StepResult executeWithTools(String task, String plan, String sessionId) {
        return executeWithTools("multi-user", task, plan, sessionId);
    }

    public StepResult executeWithTools(String userId, String task, String plan, String sessionId) {
        String executorSystem = """
                你是任务执行专家，需要结合工具执行计划并给出可靠结果。
                任务计划：
                %s

                请按计划逐步执行，优先调用工具获取真实数据。若工具失败，需要说明原因并给出替代方案。
                """.formatted(plan);

        ToolExecutionContext.set(userId, sessionId, "multi");
        try {
            ChatResponse response = chatClientBuilder.clone()
                    .defaultTools(searchTools, weatherTools, dataAnalysisTools, codeTools)
                    .defaultAdvisors(
                            new SimpleLoggerAdvisor(),
                            MessageChatMemoryAdvisor.builder(memoryService.getOrCreateMemory(sessionId)).build()
                    )
                    .build()
                    .prompt()
                    .system(executorSystem)
                    .user(task)
                    .advisors(a -> a.param(CONVERSATION_ID, sessionId))
                    .call()
                    .chatResponse();
            return StepResult.from(response);
        } finally {
            ToolExecutionContext.clear();
        }
    }

    public StepResult critique(String originalTask, String executionResult, String sessionId) {
        String criticSystem = """
                你是质量审查专家，需要检查结果是否完整、准确、可直接交付。
                原始任务：
                %s

                执行结果：
                %s

                如果结果已经足够好，直接输出最终答案；否则指出问题并补充修正。
                """.formatted(originalTask, executionResult);

        ChatResponse response = chatClientBuilder.clone()
                .build()
                .prompt()
                .system(criticSystem)
                .user("请审查执行结果并输出最终答案")
                .advisors(a -> a.param(CONVERSATION_ID, sessionId))
                .call()
                .chatResponse();
        return StepResult.from(response);
    }

    public Map<String, String> executeParallelTasks(Map<String, String> tasks) {
        return tasks.entrySet().parallelStream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            try {
                                return chatClientBuilder.clone()
                                        .build()
                                        .prompt()
                                        .user(entry.getValue())
                                        .call()
                                        .content();
                            } catch (Exception ex) {
                                log.error("子任务执行失败, key={}, error={}", entry.getKey(), ex.getMessage());
                                return "执行失败: " + ex.getMessage();
                            }
                        }
                ));
    }

    @Getter
    public static class StepResult {
        private final String content;
        private final int promptTokens;
        private final int completionTokens;

        public StepResult(String content, int promptTokens, int completionTokens) {
            this.content = content;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
        }

        static StepResult from(ChatResponse response) {
            String text = "";
            int prompt = 0;
            int completion = 0;
            if (response != null) {
                if (response.getResult() != null && response.getResult().getOutput() != null) {
                    text = response.getResult().getOutput().getText();
                }
                if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                    var usage = response.getMetadata().getUsage();
                    prompt = (int) usage.getPromptTokens();
                    completion = (int) usage.getCompletionTokens();
                }
            }
            return new StepResult(text != null ? text : "", prompt, completion);
        }
    }
}
