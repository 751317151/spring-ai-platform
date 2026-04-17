package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.config.ToolsProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AgentRuntimeIsolationService {

    private static final String WILDCARD = "*";
    private static final long WAIT_SLICE_MS = 250L;
    private static final String SEMAPHORE_KEY_PREFIX = "ai:semaphore:agent:";
    private static final int SEMAPHORE_TTL_SECONDS = 300;

    private final ToolsProperties toolsProperties;
    private final StringRedisTemplate redisTemplate;
    private final boolean distributedMode;

    private final Map<String, RuntimeState> stateByAgent = new ConcurrentHashMap<>();

    private DefaultRedisScript<Long> acquireScript;
    private DefaultRedisScript<Long> releaseScript;

    public AgentRuntimeIsolationService(ToolsProperties toolsProperties) {
        this(toolsProperties, null, false);
    }

    public AgentRuntimeIsolationService(ToolsProperties toolsProperties,
                                        StringRedisTemplate redisTemplate,
                                        @Value("${agent.isolation.distributed:false}") boolean distributedMode) {
        this.toolsProperties = toolsProperties;
        this.redisTemplate = redisTemplate;
        this.distributedMode = distributedMode;
    }

    @PostConstruct
    public void initScripts() {
        if (!distributedMode || redisTemplate == null) {
            log.info("AgentRuntimeIsolation initialized: distributedMode={}", distributedMode);
            return;
        }
        acquireScript = new DefaultRedisScript<>();
        acquireScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/distributed_semaphore_acquire.lua")));
        acquireScript.setResultType(Long.class);

        releaseScript = new DefaultRedisScript<>();
        releaseScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/distributed_semaphore_release.lua")));
        releaseScript.setResultType(Long.class);

        log.info("AgentRuntimeIsolation initialized: distributedMode={}", distributedMode);
    }

    public RuntimeIsolationDecision acquire(String agentType) {
        String normalizedAgent = normalizeAgent(agentType);
        int maxConcurrency = getMaxConcurrency(agentType);
        int maxQueueDepth = getMaxQueueDepth(agentType);
        long queueWaitTimeoutMs = getQueueWaitTimeoutMs(agentType, 0L);
        if (maxConcurrency <= 0) {
            return RuntimeIsolationDecision.allowed(-1, 0, currentActive(agentType), currentWaiting(agentType), false, 0L);
        }

        if (distributedMode) {
            return acquireDistributed(normalizedAgent, maxConcurrency, maxQueueDepth);
        }

        return acquireLocal(normalizedAgent, maxConcurrency, maxQueueDepth, queueWaitTimeoutMs);
    }

    public void release(String agentType) {
        String normalizedAgent = normalizeAgent(agentType);
        if (distributedMode) {
            releaseDistributed(normalizedAgent);
            return;
        }
        releaseLocal(normalizedAgent);
    }

    // --- Distributed mode (Redis Lua) ---

    private RuntimeIsolationDecision acquireDistributed(String normalizedAgent, int maxConcurrency, int maxQueueDepth) {
        try {
            String key = SEMAPHORE_KEY_PREFIX + normalizedAgent;
            Long result = redisTemplate.execute(
                    acquireScript,
                    Collections.singletonList(key),
                    String.valueOf(maxConcurrency),
                    String.valueOf(SEMAPHORE_TTL_SECONDS));

            if (result != null && result == 1L) {
                int active = getDistributedActive(normalizedAgent);
                return RuntimeIsolationDecision.allowed(maxConcurrency, maxQueueDepth, active, 0, false, 0L);
            }

            int active = getDistributedActive(normalizedAgent);
            return RuntimeIsolationDecision.denied(
                    "AGENT_RUNTIME_CONCURRENCY_DENIED",
                    "The current agent has reached its runtime concurrency limit (distributed)",
                    "agent:" + normalizedAgent,
                    "active=" + active + ", maxConcurrency=" + maxConcurrency);
        } catch (Exception e) {
            log.warn("[Isolation] Redis semaphore acquire failed, falling back to local: agent={}, error={}",
                    normalizedAgent, e.getMessage());
            return acquireLocal(normalizedAgent, maxConcurrency, 0, 0L);
        }
    }

    private void releaseDistributed(String normalizedAgent) {
        try {
            String key = SEMAPHORE_KEY_PREFIX + normalizedAgent;
            redisTemplate.execute(releaseScript, Collections.singletonList(key));
        } catch (Exception e) {
            log.warn("[Isolation] Redis semaphore release failed: agent={}, error={}",
                    normalizedAgent, e.getMessage());
            releaseLocal(normalizedAgent);
        }
    }

    private int getDistributedActive(String normalizedAgent) {
        try {
            String val = redisTemplate.opsForValue().get(SEMAPHORE_KEY_PREFIX + normalizedAgent);
            return val != null ? Integer.parseInt(val) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // --- Local mode (original in-memory) ---

    private RuntimeIsolationDecision acquireLocal(String normalizedAgent, int maxConcurrency, int maxQueueDepth, long queueWaitTimeoutMs) {
        RuntimeState state = stateByAgent.computeIfAbsent(normalizedAgent, ignored -> new RuntimeState());
        long startedAt = System.currentTimeMillis();
        synchronized (state) {
            RuntimeIsolationDecision immediateDecision = tryAcquireImmediately(state, maxConcurrency, maxQueueDepth);
            if (immediateDecision != null) {
                return immediateDecision;
            }
            RuntimeIsolationDecision queueDecision = validateQueueAvailability(state, normalizedAgent, maxConcurrency, maxQueueDepth);
            if (queueDecision != null) {
                return queueDecision;
            }
            return enqueueAndAwaitSlot(state, normalizedAgent, maxConcurrency, maxQueueDepth, queueWaitTimeoutMs, startedAt);
        }
    }

    private void releaseLocal(String normalizedAgent) {
        RuntimeState state = stateByAgent.get(normalizedAgent);
        if (state == null) {
            return;
        }
        synchronized (state) {
            state.active = Math.max(0, state.active - 1);
            state.notifyAll();
        }
    }

    public int currentActive(String agentType) {
        String normalizedAgent = normalizeAgent(agentType);
        if (distributedMode) {
            return getDistributedActive(normalizedAgent);
        }
        RuntimeState state = stateByAgent.get(normalizedAgent);
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            return state.active;
        }
    }

    public int currentWaiting(String agentType) {
        RuntimeState state = stateByAgent.get(normalizeAgent(agentType));
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            return state.waiting;
        }
    }

    public int getMaxConcurrency(String agentType) {
        Integer configured = getConfiguredValue(
                agentType,
                toolsProperties.getSecurity().getAgentMaxConcurrency()
        );
        return configured == null ? 0 : configured;
    }

    public int getMaxQueueDepth(String agentType) {
        Integer configured = getConfiguredValue(
                agentType,
                toolsProperties.getSecurity().getAgentMaxQueueDepth()
        );
        return configured == null ? 0 : configured;
    }

    public long getQueueWaitTimeoutMs(String agentType, long fallback) {
        Long configured = getConfiguredValue(
                agentType,
                toolsProperties.getSecurity().getAgentQueueWaitTimeoutMs()
        );
        return configured == null || configured <= 0 ? fallback : configured;
    }

    public long getRequestTimeoutMs(String agentType, long fallback) {
        Long configured = getConfiguredValue(
                agentType,
                toolsProperties.getSecurity().getAgentRequestTimeoutMs()
        );
        return configured == null || configured <= 0 ? fallback : configured;
    }

    public long getStreamTimeoutMs(String agentType, long fallback) {
        Long configured = getConfiguredValue(
                agentType,
                toolsProperties.getSecurity().getAgentStreamTimeoutMs()
        );
        return configured == null || configured <= 0 ? fallback : configured;
    }

    private <T> T getConfiguredValue(String agentType, Map<String, T> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (values.containsKey(agentType)) {
            return values.get(agentType);
        }
        return values.get(WILDCARD);
    }

    private String normalizeAgent(String agentType) {
        return agentType == null || agentType.isBlank() ? WILDCARD : agentType.trim();
    }

    private RuntimeIsolationDecision tryAcquireImmediately(RuntimeState state, int maxConcurrency, int maxQueueDepth) {
        if (state.active >= maxConcurrency) {
            return null;
        }
        state.active++;
        return RuntimeIsolationDecision.allowed(
                maxConcurrency,
                maxQueueDepth,
                state.active,
                state.waiting,
                false,
                0L
        );
    }

    private RuntimeIsolationDecision validateQueueAvailability(RuntimeState state,
                                                               String normalizedAgent,
                                                               int maxConcurrency,
                                                               int maxQueueDepth) {
        if (maxQueueDepth <= 0) {
            return deniedConcurrency(normalizedAgent, state, maxConcurrency);
        }
        if (state.waiting >= maxQueueDepth) {
            return deniedQueueDepth(normalizedAgent, state, maxConcurrency, maxQueueDepth);
        }
        return null;
    }

    private RuntimeIsolationDecision enqueueAndAwaitSlot(RuntimeState state,
                                                         String normalizedAgent,
                                                         int maxConcurrency,
                                                         int maxQueueDepth,
                                                         long queueWaitTimeoutMs,
                                                         long startedAt) {
        state.waiting++;
        try {
            long deadline = queueWaitTimeoutMs <= 0 ? Long.MAX_VALUE : startedAt + queueWaitTimeoutMs;
            while (state.active >= maxConcurrency) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return deniedQueueTimeout(normalizedAgent, state, maxConcurrency, maxQueueDepth, queueWaitTimeoutMs);
                }
                try {
                    state.wait(Math.min(remaining, WAIT_SLICE_MS));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return deniedQueueInterrupted(normalizedAgent, state);
                }
            }
            state.active++;
            long waitedMs = Math.max(0L, System.currentTimeMillis() - startedAt);
            return RuntimeIsolationDecision.allowed(
                    maxConcurrency,
                    maxQueueDepth,
                    state.active,
                    Math.max(0, state.waiting - 1),
                    true,
                    waitedMs
            );
        } finally {
            state.waiting = Math.max(0, state.waiting - 1);
        }
    }

    private RuntimeIsolationDecision deniedConcurrency(String normalizedAgent, RuntimeState state, int maxConcurrency) {
        return RuntimeIsolationDecision.denied(
                "AGENT_RUNTIME_CONCURRENCY_DENIED",
                "The current agent has reached its runtime concurrency limit",
                "agent:" + normalizedAgent,
                "active=" + state.active + ", maxConcurrency=" + maxConcurrency
        );
    }

    private RuntimeIsolationDecision deniedQueueDepth(String normalizedAgent,
                                                      RuntimeState state,
                                                      int maxConcurrency,
                                                      int maxQueueDepth) {
        return RuntimeIsolationDecision.denied(
                "AGENT_RUNTIME_QUEUE_DENIED",
                "The current agent has reached its runtime queue limit",
                "agent:" + normalizedAgent,
                "active=" + state.active + ", waiting=" + state.waiting + ", maxConcurrency=" + maxConcurrency + ", maxQueueDepth=" + maxQueueDepth
        );
    }

    private RuntimeIsolationDecision deniedQueueTimeout(String normalizedAgent,
                                                        RuntimeState state,
                                                        int maxConcurrency,
                                                        int maxQueueDepth,
                                                        long queueWaitTimeoutMs) {
        return RuntimeIsolationDecision.denied(
                "AGENT_RUNTIME_QUEUE_TIMEOUT",
                "The current agent runtime queue wait timed out",
                "agent:" + normalizedAgent,
                "active=" + state.active + ", waiting=" + state.waiting + ", maxConcurrency=" + maxConcurrency + ", maxQueueDepth=" + maxQueueDepth + ", queueWaitTimeoutMs=" + queueWaitTimeoutMs
        );
    }

    private RuntimeIsolationDecision deniedQueueInterrupted(String normalizedAgent, RuntimeState state) {
        return RuntimeIsolationDecision.denied(
                "AGENT_RUNTIME_QUEUE_INTERRUPTED",
                "The current agent runtime queue wait was interrupted",
                "agent:" + normalizedAgent,
                "active=" + state.active + ", waiting=" + state.waiting
        );
    }

    public record RuntimeIsolationDecision(
            boolean allowed,
            String reasonCode,
            String reasonMessage,
            String resource,
            String detail,
            int maxConcurrency,
            int maxQueueDepth,
            int activeCount,
            int waitingCount,
            boolean queued,
            long waitedMs
    ) {
        public static RuntimeIsolationDecision allowed(int maxConcurrency,
                                                       int maxQueueDepth,
                                                       int activeCount,
                                                       int waitingCount,
                                                       boolean queued,
                                                       long waitedMs) {
            return new RuntimeIsolationDecision(true, null, null, null, null, maxConcurrency, maxQueueDepth, activeCount, waitingCount, queued, waitedMs);
        }

        public static RuntimeIsolationDecision denied(String reasonCode,
                                                      String reasonMessage,
                                                      String resource,
                                                      String detail) {
            return new RuntimeIsolationDecision(false, reasonCode, reasonMessage, resource, detail, -1, -1, -1, -1, false, 0L);
        }
    }

    private static final class RuntimeState {
        private int active;
        private int waiting;
    }
}
