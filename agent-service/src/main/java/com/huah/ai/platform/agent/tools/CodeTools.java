package com.huah.ai.platform.agent.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码开发工具集 — ChatModel 嵌套调用实现 AI 代码分析与审查
 */
@Slf4j
@Component
public class CodeTools {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public CodeTools(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.objectMapper = new ObjectMapper();
    }

    @Tool(description = "分析代码片段，预测其执行结果。基于 AI 分析而非实际执行，确保安全。")
    public Map<String, Object> executeCode(
            @ToolParam(description = "编程语言: java|python|javascript|go") String language,
            @ToolParam(description = "要分析的代码") String code) {
        log.info("[Tool] executeCode (AI analysis): language={}, codeLength={}", language, code.length());
        try {
            String prompt = String.format("""
                    请分析以下 %s 代码，预测其执行结果。

                    要求：
                    1. 给出预期的标准输出 (stdout)
                    2. 指出可能的错误输出 (stderr)
                    3. 预测退出码 (0=成功，非0=失败)
                    4. 简要说明代码逻辑

                    请严格按如下格式输出（每项占一行，不要输出其他内容）：
                    STDOUT: <预期输出>
                    STDERR: <错误输出，无则留空>
                    EXIT_CODE: <退出码>
                    ANALYSIS: <简要分析>

                    代码：
                    ```%s
                    %s
                    ```
                    """, language, language, code);

            String aiResponse = chatModel.call(prompt);

            String stdout = extractField(aiResponse, "STDOUT");
            String stderr = extractField(aiResponse, "STDERR");
            String exitCodeStr = extractField(aiResponse, "EXIT_CODE");
            String analysis = extractField(aiResponse, "ANALYSIS");

            int exitCode;
            try {
                exitCode = Integer.parseInt(exitCodeStr.trim());
            } catch (NumberFormatException e) {
                exitCode = 0;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("language", language);
            result.put("stdout", stdout);
            result.put("stderr", stderr);
            result.put("exitCode", exitCode);
            result.put("analysis", analysis);
            result.put("note", "结果基于 AI 分析预测，非实际执行");
            return result;
        } catch (Exception e) {
            log.error("[Tool] executeCode analysis failed: {}", e.getMessage());
            return Map.of("error", "代码分析失败: " + e.getMessage());
        }
    }

    @Tool(description = "在 Git 仓库中搜索代码（此功能需要配置 GitHub API Token 后启用）")
    public List<Map<String, String>> searchGitRepo(
            @ToolParam(description = "仓库名称或URL") String repo,
            @ToolParam(description = "搜索关键词或正则表达式") String query) {
        log.info("[Tool] searchGitRepo (not yet enabled): repo={}, query={}", repo, query);
        return List.of(Map.of(
                "info", "Git 仓库搜索功能暂未接入真实 API，请配置 GitHub Token 后启用",
                "repo", repo,
                "query", query
        ));
    }

    @Tool(description = "审查代码，检查潜在问题、代码规范、安全风险，返回详细审查报告")
    public Map<String, Object> reviewCode(
            @ToolParam(description = "编程语言") String language,
            @ToolParam(description = "要审查的代码") String code) {
        log.info("[Tool] reviewCode: language={}, codeLength={}", language, code.length());
        try {
            String prompt = String.format("""
                    你是一位资深代码审查专家。请对以下 %s 代码进行全面审查。

                    审查维度：
                    1. 安全漏洞（SQL注入、XSS、敏感信息泄露等）
                    2. 代码规范（命名、格式、注释）
                    3. 性能问题（内存泄漏、不必要的对象创建、N+1查询等）
                    4. 逻辑错误（边界条件、空指针、并发问题）
                    5. 最佳实践建议

                    请以如下 JSON 格式输出（不要使用 markdown 代码块包裹）：
                    {
                      "issues": [
                        {"severity": "HIGH|MEDIUM|LOW", "type": "安全|规范|性能|逻辑", "message": "问题描述", "line": 行号或0, "suggestion": "修复建议"}
                      ],
                      "score": "X/10",
                      "summary": "整体评价"
                    }

                    代码：
                    ```%s
                    %s
                    ```
                    """, language, language, code);

            String aiResponse = chatModel.call(prompt);

            // Try to parse as JSON
            try {
                String json = extractJson(aiResponse);
                Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
                return parsed;
            } catch (Exception parseEx) {
                log.warn("[Tool] reviewCode: AI response is not valid JSON, returning raw text");
                Map<String, Object> result = new HashMap<>();
                result.put("rawReview", aiResponse);
                result.put("note", "AI 返回了非结构化审查结果");
                return result;
            }
        } catch (Exception e) {
            log.error("[Tool] reviewCode failed: {}", e.getMessage());
            return Map.of("error", "代码审查失败: " + e.getMessage());
        }
    }

    private String extractField(String text, String fieldName) {
        String prefix = fieldName + ":";
        int start = text.indexOf(prefix);
        if (start < 0) return "";
        start += prefix.length();
        int end = text.indexOf("\n", start);
        if (end < 0) end = text.length();
        return text.substring(start, end).trim();
    }

    private String extractJson(String text) {
        String json = text.trim();
        // Remove markdown code block wrappers if present
        if (json.contains("```json")) {
            json = json.substring(json.indexOf("```json") + 7);
            json = json.substring(0, json.indexOf("```"));
        } else if (json.contains("```")) {
            json = json.substring(json.indexOf("```") + 3);
            if (json.startsWith("\n")) json = json.substring(1);
            json = json.substring(0, json.indexOf("```"));
        }
        return json.trim();
    }
}
