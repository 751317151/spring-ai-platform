package com.huah.ai.platform.agent.multi;

import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import com.huah.ai.platform.agent.tools.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.Map;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * Multi-Agent 协作框架
 *
 * 流程：Planner → Executor（带工具） → Critic
 * 每一步使用 chatClientBuilder.clone() 避免污染共享 Builder
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentOrchestrator {

    private final ChatClient.Builder chatClientBuilder;
    private final ConversationMemoryService memoryService;

    // 注入通用工具供 Executor 使用
    private final SearchTools searchTools;
    private final WeatherTools weatherTools;
    private final DataAnalysisTools dataAnalysisTools;
    private final CodeTools codeTools;

    private static final String PLANNER_SYSTEM = """
            你是任务规划专家。分析用户复杂请求，将其分解为可执行的子任务列表。
            输出格式：
            ## 任务分析
            [对任务的理解]

            ## 执行计划
            1. [子任务1] - 使用工具：[工具名]
            2. [子任务2] - 使用工具：[工具名]
            ...

            ## 预期结果
            [完成后的预期输出]

            可用工具包括：网络搜索(webSearch/summarizeUrl)、天气查询(getWeather/getWeatherForecast)、\
            数据分析(executeQuery/analyzeDataset)、代码分析(executeCode/reviewCode)。
            如果任务不需要工具，直接说明执行策略即可。
            """;

    /**
     * 执行复杂多步任务（Planner → Executor → Critic）
     */
    public StepResult executeComplexTask(String userId, String sessionId, String task) {
        log.info("Multi-Agent 任务开始: userId={}, task={}", userId, task);
        String internalId = sessionId + "-multi-" + System.currentTimeMillis();

        StepResult planResult = planTask(task, internalId + "-planner");
        log.debug("任务规划完成, tokens={}/{}", planResult.promptTokens, planResult.completionTokens);

        StepResult execResult = executeWithTools(task, planResult.content, internalId + "-executor");

        StepResult criticResult = critique(task, execResult.content, internalId + "-critic");

        log.info("Multi-Agent 任务完成, 总 tokens={}/{}",
                planResult.promptTokens + execResult.promptTokens + criticResult.promptTokens,
                planResult.completionTokens + execResult.completionTokens + criticResult.completionTokens);

        return new StepResult(
                criticResult.content,
                planResult.promptTokens + execResult.promptTokens + criticResult.promptTokens,
                planResult.completionTokens + execResult.completionTokens + criticResult.completionTokens
        );
    }

    /** Step 1: Planner 分解任务 */
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

    /** Step 2: Executor 执行（携带工具 + 记忆） */
    public StepResult executeWithTools(String task, String plan, String sessionId) {
        String executorSystem = String.format("""
                你是任务执行专家，负责使用可用工具完成具体任务。

                任务计划：
                %s

                请根据计划逐步执行，充分利用可用工具获取真实数据。给出详细执行结果。
                如果工具调用失败，说明失败原因并尝试替代方案。
                """, plan);

        ChatResponse response = chatClientBuilder.clone()
                .defaultTools(searchTools, weatherTools, dataAnalysisTools, codeTools)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(
                                memoryService.getOrCreateMemory(sessionId)
                        ).build()
                )
                .build()
                .prompt()
                .system(executorSystem)
                .user(task)
                .advisors(a -> a.param(CONVERSATION_ID, sessionId))
                .call()
                .chatResponse();

        return StepResult.from(response);
    }

    /** Step 3: Critic 综合评审 */
    public StepResult critique(String originalTask, String executionResult, String sessionId) {
        String criticSystem = String.format("""
                你是质量审查专家，评估任务执行结果的完整性和准确性。

                原始任务：%s

                执行结果：%s

                请从以下维度评审：
                1. 结果是否完整回答了用户的问题
                2. 数据是否准确、有无遗漏
                3. 逻辑是否连贯

                如果结果满意，直接给出最终综合答案（不需要重复评审过程）。
                如果有问题，指出不足并补充完善。
                """, originalTask, executionResult);

        ChatResponse response = chatClientBuilder.clone()
                .build()
                .prompt()
                .system(criticSystem)
                .user("请对执行结果进行评审并给出最终答案")
                .advisors(a -> a.param(CONVERSATION_ID, sessionId))
                .call()
                .chatResponse();

        return StepResult.from(response);
    }

    /**
     * 并行执行多个独立子任务
     */
    public Map<String, String> executeParallelTasks(Map<String, String> tasks) {
        return tasks.entrySet().parallelStream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            try {
                                return chatClientBuilder.clone()
                                        .build()
                                        .prompt()
                                        .user(e.getValue())
                                        .call()
                                        .content();
                            } catch (Exception ex) {
                                log.error("子任务执行失败: key={}, error={}", e.getKey(), ex.getMessage());
                                return "执行失败: " + ex.getMessage();
                            }
                        }
                ));
    }

    /**
     * 单步执行结果，携带内容和 Token 用量
     */
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
