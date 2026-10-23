package com.simpleshop.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档元数据与 Bearer 鉴权方案。
 *
 * <p>生产环境可通过 SPRINGDOC_ENABLED=false 关闭文档端点。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI simpleShopOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("simple-shop API")
                        .version("0.1.0")
                        .description("阶段二 REST 接口；买家与卖家令牌按角色隔离。"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("token")));
    }
}
