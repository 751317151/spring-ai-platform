package com.huah.ai.platform.rag.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class RagQueryRequest {
    private String question;
    private Long knowledgeBaseId;
    private Integer topK;
    /** 是否返回来源文档片段 */
    private boolean returnSources = false;
    /** 对话历史 */
    private List<Map<String, String>> history;
}
