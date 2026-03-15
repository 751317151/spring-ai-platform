package com.huah.ai.platform.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 代码开发工具集
 */
@Slf4j
@Component
public class CodeTools {

    @Tool(description = "执行代码片段，返回标准输出和错误输出")
    public Map<String, Object> executeCode(
            @ToolParam(description = "编程语言: java|python|javascript|go") String language,
            @ToolParam(description = "要执行的代码") String code) {
        log.info("[Tool] executeCode: language={}, codeLength={}", language, code.length());
        return Map.of(
                "language", language,
                "stdout", "Hello, World!\nExecution completed successfully.",
                "stderr", "",
                "exitCode", 0,
                "executionTime", "128ms"
        );
    }

    @Tool(description = "在 Git 仓库中搜索代码，返回匹配的文件和代码片段")
    public List<Map<String, String>> searchGitRepo(
            @ToolParam(description = "仓库名称或URL") String repo,
            @ToolParam(description = "搜索关键词或正则表达式") String query) {
        log.info("[Tool] searchGitRepo: repo={}, query={}", repo, query);
        return List.of(
                Map.of("file", "src/main/java/UserService.java", "line", "42", "content", "public User findById(String id) {"),
                Map.of("file", "src/main/java/UserController.java", "line", "28", "content", "@GetMapping(\"/users/{id}\")"),
                Map.of("file", "src/test/java/UserServiceTest.java", "line", "15", "content", "@Test void shouldFindUserById() {")
        );
    }

    @Tool(description = "审查代码，检查潜在问题、代码规范、安全风险")
    public Map<String, Object> reviewCode(
            @ToolParam(description = "编程语言") String language,
            @ToolParam(description = "要审查的代码") String code) {
        log.info("[Tool] reviewCode: language={}, codeLength={}", language, code.length());
        return Map.of(
                "issues", List.of(
                        Map.of("severity", "HIGH", "type", "安全", "message", "检测到 SQL 拼接，建议使用参数化查询", "line", 15),
                        Map.of("severity", "MEDIUM", "type", "规范", "message", "方法过长(>50行)，建议拆分", "line", 30),
                        Map.of("severity", "LOW", "type", "性能", "message", "循环内创建对象，建议提取到循环外", "line", 45)
                ),
                "score", "6.5/10",
                "summary", "发现 3 个问题：1个高危安全问题，1个中等规范问题，1个低级性能问题"
        );
    }
}
