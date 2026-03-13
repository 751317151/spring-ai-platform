package com.example.aiplatform.agent;

import com.example.aiplatform.model.AgentMemory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MemoryService {

    private final Map<String, AgentMemory> memoryStore = new ConcurrentHashMap<>();

    public AgentMemory load(String userId) {
        return memoryStore.getOrDefault(userId, new AgentMemory(userId, "无短期记忆", "无长期画像", Instant.now()));
    }

    public AgentMemory update(String userId, String shortTermSummary, String longTermProfile) {
        AgentMemory memory = new AgentMemory(userId, shortTermSummary, longTermProfile, Instant.now());
        memoryStore.put(userId, memory);
        return memory;
    }
}
