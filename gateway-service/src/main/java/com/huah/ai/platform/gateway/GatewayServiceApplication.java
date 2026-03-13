package com.huah.ai.platform.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI 模型网关 — 多模型路由、负载均衡、熔断降级
 *
 * 注意：gateway-service 包含 3 个 Spring AI starter（openai/anthropic/ollama）
 * 每个 starter 会尝试自动配置一个 ChatModel bean。
 * ModelGatewayService 手动构建各 ChatModel，通过 spring.ai.openai.chat.enabled=false 等
 * 配置关闭 starter 的自动 ChatModel 注册，避免 bean 冲突。
 */
@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties
@ComponentScan(basePackages = {"com.huah.ai.platform.gateway", "com.huah.ai.platform.common"})
public class GatewayServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
