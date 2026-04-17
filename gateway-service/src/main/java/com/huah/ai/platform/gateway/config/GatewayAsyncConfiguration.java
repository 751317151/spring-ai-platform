package com.huah.ai.platform.gateway.config;

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
public class GatewayAsyncConfiguration {

    @Value("${gateway.executor.core-pool-size:8}")
    private int corePoolSize;

    @Value("${gateway.executor.max-pool-size:32}")
    private int maxPoolSize;

    @Value("${gateway.executor.queue-capacity:64}")
    private int queueCapacity;

    @Value("${gateway.executor.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Bean(name = "gatewaySseExecutor", destroyMethod = "shutdown")
    public ExecutorService gatewaySseExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new NamedDaemonThreadFactory("gateway-sse-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        log.info("GatewaySseExecutor initialized: core={}, max={}, queue={}, keepAlive={}s",
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
