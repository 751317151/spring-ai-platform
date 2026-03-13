package com.huah.ai.platform.agent.multi;

import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Multi-Agent 协作框架
 *
 * 流程：Planner → Executor（带工具） → Critic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentOrchestrator {

    private final ChatClient.Builder chatClientBuilder;
    private final ConversationMemoryService memoryService;

    private static final String PLANNER_SYSTEM = """
            你是任务规划专家。分析用户复杂请求，将其分解为可执行的子任务列表。
            输出格式（JSON）：
            {"tasks":[{"id":1,"description":"子任务描述","tool":"queryJira|queryInventory|..."}],"summary":"整体任务描述"}
            只输出 JSON，不要有其他内容。
            """;

    /**
     * 执行复杂多步任务（Planner → Executor → Critic）
     */
    public String executeComplexTask(String userId, String sessionId, String task) {
        log.info("Multi-Agent 任务开始: userId={}, task={}", userId, task);

        // Step 1: Planner 分解任务
        String plan = planTask(task);
        log.debug("任务规划: {}", plan);

        // Step 2: Executor 执行（携带工具 + 记忆）
        String executionResult = executeWithTools(task, plan, sessionId);

        // Step 3: Critic 综合评审
        String finalResult = critique(task, executionResult);
        log.info("Multi-Agent 任务完成");
        return finalResult;
    }

    private String planTask(String task) {
        return chatClientBuilder.build()
                .prompt()
                .system(PLANNER_SYSTEM)
                .user("请分解以下任务：" + task)
                .call()
                .content();
    }

    private String executeWithTools(String task, String plan, String sessionId) {
        String executorSystem = String.format("""
                你是任务执行专家，负责使用工具完成具体任务。
                任务计划：%s
                请使用可用工具完成任务，给出详细执行结果。
                """, plan);

        return chatClientBuilder
                .defaultAdvisors(
                    new SimpleLoggerAdvisor(),
                    MessageChatMemoryAdvisor.builder(
                        memoryService.getOrCreateMemory(sessionId + "-executor")
                    ).build()
                )
                .build()
                .prompt()
                .system(executorSystem)
                .user(task)
                .call()
                .content();
    }

    private String critique(String originalTask, String executionResult) {
        String criticSystem = String.format("""
                你是质量审查专家，评估任务执行结果。
                原始任务：%s
                执行结果：%s
                请判断结果是否完整，是否有遗漏，并给出最终综合答案。
                """, originalTask, executionResult);

        return chatClientBuilder.build()
                .prompt()
                .system(criticSystem)
                .user("请对执行结果进行评审并给出最终答案")
                .call()
                .content();
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
                                return chatClientBuilder
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
}
