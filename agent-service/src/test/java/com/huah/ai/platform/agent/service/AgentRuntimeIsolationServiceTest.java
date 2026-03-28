package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.config.ToolsProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeIsolationServiceTest {

    @Test
    void shouldQueueAndAcquireWhenSlotIsReleased() throws Exception {
        ToolsProperties properties = new ToolsProperties();
        properties.getSecurity().getAgentMaxConcurrency().put("rd", 1);
        properties.getSecurity().getAgentMaxQueueDepth().put("rd", 1);
        properties.getSecurity().getAgentQueueWaitTimeoutMs().put("rd", 1000L);
        AgentRuntimeIsolationService service = new AgentRuntimeIsolationService(properties);

        AgentRuntimeIsolationService.RuntimeIsolationDecision first = service.acquire("rd");
        assertTrue(first.allowed());

        CountDownLatch acquired = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            Future<AgentRuntimeIsolationService.RuntimeIsolationDecision> future = executor.submit(() -> {
                acquired.countDown();
                return service.acquire("rd");
            });
            assertTrue(acquired.await(200, TimeUnit.MILLISECONDS));
            Thread.sleep(80L);
            service.release("rd");

            AgentRuntimeIsolationService.RuntimeIsolationDecision queued = future.get(2, TimeUnit.SECONDS);
            assertTrue(queued.allowed());
            assertTrue(queued.queued());
            assertTrue(queued.waitedMs() >= 50L);
            assertEquals(1, service.currentActive("rd"));
        } finally {
            service.release("rd");
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectWhenQueueDepthIsExceeded() {
        ToolsProperties properties = new ToolsProperties();
        properties.getSecurity().getAgentMaxConcurrency().put("rd", 1);
        properties.getSecurity().getAgentMaxQueueDepth().put("rd", 1);
        properties.getSecurity().getAgentQueueWaitTimeoutMs().put("rd", 200L);
        AgentRuntimeIsolationService service = new AgentRuntimeIsolationService(properties);

        AgentRuntimeIsolationService.RuntimeIsolationDecision first = service.acquire("rd");
        assertTrue(first.allowed());

        Thread queuedThread = new Thread(() -> {
            AgentRuntimeIsolationService.RuntimeIsolationDecision ignored = service.acquire("rd");
            if (ignored.allowed()) {
                service.release("rd");
            }
        });
        queuedThread.start();

        try {
            Thread.sleep(50L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        AgentRuntimeIsolationService.RuntimeIsolationDecision denied = service.acquire("rd");
        assertFalse(denied.allowed());
        assertEquals("AGENT_RUNTIME_QUEUE_DENIED", denied.reasonCode());

        service.release("rd");
        try {
            queuedThread.join(1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
