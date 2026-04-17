package com.huah.ai.platform.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
public class AgentAsyncConfiguration {

    @Value("${agent.executor.core-pool-size:16}")
    private int corePoolSize;

    @Value("${agent.executor.max-pool-size:64}")
    private int maxPoolSize;

    @Value("${agent.executor.queue-capacity:128}")
    private int queueCapacity;

    @Value("${agent.executor.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Bean(name = "agentControllerExecutor", destroyMethod = "shutdown")
    public ExecutorService agentControllerExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new NamedDaemonThreadFactory("agent-controller-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        log.info("AgentControllerExecutor initialized: core={}, max={}, queue={}, keepAlive={}s",
                corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds);
        return executor;
    }

    private static final class NamedDaemonThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger sequence = new AtomicInteger(1);

        private NamedDaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
