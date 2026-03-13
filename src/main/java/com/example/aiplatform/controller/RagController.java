package com.example.aiplatform.controller;

import com.example.aiplatform.dto.RagIngestRequest;
import com.example.aiplatform.dto.RagQueryRequest;
import com.example.aiplatform.model.KnowledgeChunk;
import com.example.aiplatform.rag.RagService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ingest")
    public List<KnowledgeChunk> ingest(@Valid @RequestBody RagIngestRequest request) {
        return ragService.ingest(request);
    }

    @PostMapping("/query")
    public Map<String, String> query(@Valid @RequestBody RagQueryRequest request) {
        return Map.of("answer", ragService.answer(request));
    }
}
