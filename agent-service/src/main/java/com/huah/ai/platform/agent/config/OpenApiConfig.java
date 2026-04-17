package com.huah.ai.platform.agent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI agentServiceOpenApi() {
        String securitySchemeName = "BearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("AI Agent Service API")
                        .description("企业级 AI Agent 服务 - 多垂直域 Agent、工具调用、Multi-Agent 协作、记忆管理")
                        .version("1.0.0")
                        .contact(new Contact().name("AI Platform Team")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .schemaRequirement(securitySchemeName, new SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));
    }
}
