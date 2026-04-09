package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.rag.model.RetrievalRewriteResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class QueryRewriteService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern LEADING_FILLERS =
            Pattern.compile("^(请问一下|请问|请帮我|帮我|麻烦你|麻烦|请你|请|能否|能不能|可以|帮忙|想了解|我想知道)+");
    private static final Pattern TRAILING_FILLERS =
            Pattern.compile("(是什么|是啥|怎么做|怎么办|怎么写|怎么查|有哪些|是什么样的|告诉我|说明一下|解释一下|介绍一下|总结一下)+$");
    private static final Pattern SPLITTER =
            Pattern.compile("[\\s,，。；;、!?！？:/|（）()\\[\\]\\-]+|并且|并|以及|还有|给出|说明|列出|介绍|总结|分析|解释|回答");

    private static final Set<String> STOP_WORDS = Set.of(
            "这个", "那个", "一下", "一下子", "一下吧", "一下呢",
            "什么", "哪些", "如何", "怎么", "为什么", "是否",
            "请问", "帮我", "告诉", "说明", "介绍", "分析", "总结",
            "相关", "内容", "信息",
            "what", "which", "how", "why", "tell", "show", "please",
            "about", "with", "from", "that", "this");

    public RetrievalRewriteResult rewrite(String question) {
        String originalQuery = normalize(question);
        String retrievalQuery = cleanupForRetrieval(originalQuery);
        if (retrievalQuery.isBlank()) {
            retrievalQuery = originalQuery;
        }

        List<String> keywords = extractKeywords(retrievalQuery);
        List<String> alternateQueries = buildAlternateQueries(originalQuery, retrievalQuery, keywords);

        return RetrievalRewriteResult.builder()
                .originalQuery(originalQuery)
                .retrievalQuery(retrievalQuery)
                .alternateQueries(alternateQueries)
                .keywords(keywords)
                .build();
    }

    private String normalize(String question) {
        if (question == null) {
            return "";
        }
        return WHITESPACE.matcher(question.replace('\n', ' ').trim()).replaceAll(" ");
    }

    private String cleanupForRetrieval(String query) {
        String cleaned = LEADING_FILLERS.matcher(query).replaceFirst("");
        cleaned = TRAILING_FILLERS.matcher(cleaned).replaceFirst("");
        cleaned = cleaned.replaceAll("^(关于|针对|基于)", "");
        cleaned = cleaned.replaceAll("(吗|呢|呀|吧)$", "");
        cleaned = cleaned.trim();
        return cleaned.isBlank() ? query : cleaned;
    }

    private List<String> buildAlternateQueries(String originalQuery, String retrievalQuery, List<String> keywords) {
        Set<String> alternates = new LinkedHashSet<>();
        if (!retrievalQuery.equals(originalQuery)) {
            alternates.add(retrievalQuery);
        }
        if (keywords.size() >= 2) {
            alternates.add(String.join(" ", keywords));
        }
        if (keywords.size() >= 3) {
            alternates.add(String.join(" ", keywords.subList(0, Math.min(3, keywords.size()))));
        }
        return new ArrayList<>(alternates);
    }

    private List<String> extractKeywords(String query) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String segment : SPLITTER.split(query)) {
            String normalized = segment.trim();
            if (normalized.length() < 2) {
                continue;
            }
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (STOP_WORDS.contains(lower)) {
                continue;
            }
            tokens.add(normalized);
        }
        return tokens.stream().limit(6).toList();
    }
}
