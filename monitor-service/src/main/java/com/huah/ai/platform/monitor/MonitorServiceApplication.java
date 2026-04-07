package com.huah.ai.platform.monitor;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 监控服务启动类
 *
 * 排除 Hibernate JPA 自动配置（monitor-service 不使用 @Entity / JPA），
 * 但保留 DataSource + JdbcTemplate 供 MonitorController 查询共享数据库。
 *
 * 指标采集：Micrometer → Prometheus（由 AiMetricsCollector 注册自定义指标）
 * 数据存储：读取 agent-service 写入的 ai_audit_logs 表（共享 PostgreSQL）
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.huah.ai.platform.monitor", "com.huah.ai.platform.common"})
@MapperScan(basePackages = "com.huah.ai.platform.common.persistence", annotationClass = Mapper.class)
public class MonitorServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MonitorServiceApplication.class, args);
    }
}
