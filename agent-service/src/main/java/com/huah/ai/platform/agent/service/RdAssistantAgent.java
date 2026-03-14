package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * 研发助手 Agent
 * 功能：代码审查、技术文档查询、缺陷分析、技术选型建议
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RdAssistantAgent {

    private final ChatClient.Builder chatClientBuilder;
    private final ConversationMemoryService memoryService;

    private static final String SYSTEM_PROMPT = """
            你是企业研发部门的智能助手，专注于以下领域：
            1. 代码审查与优化建议
            2. 技术文档查询和解答
            3. 缺陷(Bug)分析和修复建议
            4. 技术选型分析对比
            5. API接口设计建议
            6. 性能优化方案
            
            你可以使用以下工具：
            - queryJira: 查询 Jira 缺陷系统
            - queryConfluence: 查询 Confluence 技术文档
            - querySonar: 查询 SonarQube 代码质量报告
            - getGitHistory: 获取 Git 提交历史
            
            回答要技术准确、简洁，提供可操作的建议。
            当前用户: {userId}
            """;

    public String chat(String userId, String sessionId, String message) {
        ChatMemory memory = memoryService.getOrCreateMemory(sessionId);

        return chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(new SimpleLoggerAdvisor(), MessageChatMemoryAdvisor.builder(memory).build())
                .build()
                .prompt()
                .system(s -> s.param("userId", userId))
                .user(message)
                .advisors(a -> a.param(CONVERSATION_ID, sessionId))
                .call()
                .content();
    }

    public Flux<String> chatStream(String userId, String sessionId, String message) {
        ChatMemory memory = memoryService.getOrCreateMemory(sessionId);

        return chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(new SimpleLoggerAdvisor(), MessageChatMemoryAdvisor.builder(memory).build())
                .build()
                .prompt()
                .system(s -> s.param("userId", userId))
                .user(message)
                .advisors(a -> a.param(CONVERSATION_ID, sessionId))
                .stream()
                .content();
    }
}
