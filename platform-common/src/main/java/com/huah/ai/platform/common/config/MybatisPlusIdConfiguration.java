package com.huah.ai.platform.common.config;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.huah.ai.platform.common.util.SnowflakeIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusIdConfiguration {

    @Bean
    public IdentifierGenerator identifierGenerator(SnowflakeIdGenerator snowflakeIdGenerator) {
        return new IdentifierGenerator() {
            @Override
            public Number nextId(Object entity) {
                return snowflakeIdGenerator.nextLongId();
            }

            @Override
            public String nextUUID(Object entity) {
                return snowflakeIdGenerator.nextId();
            }
        };
    }
}
