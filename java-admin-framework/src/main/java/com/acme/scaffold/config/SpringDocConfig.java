package com.acme.scaffold.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * API 文档配置：仅在 local/dev 环境启用 Swagger UI，生产环境关闭。
 */
@Configuration
@Profile({"local", "dev"})
public class SpringDocConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("Java 后端基础管理框架 API")
                .description("RBAC 权限、认证授权、性能监控、工作流审批脚手架")
                .version("1.0.0"));
    }
}
