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
                        .version("1.0.0")
                        .description("""
                                阶段二 REST 接口；买家与卖家令牌按角色隔离，不能互用。

                                全局约定：
                                - 统一响应体 {code, message, data}；业务拒绝返回 HTTP 200 + 业务码，
                                  鉴权失败 401（code 10002）、参数错误 400（code 50002，附 errors 字段）、
                                  资源不存在 404、服务端错误 500。
                                - 分页参数 page（从 1 起）与 page_size（默认 10，上限 100）；
                                  分页响应为 {items, page, page_size, total}。
                                - 需要登录的接口使用 Authorization: Bearer <token>；
                                  /api/public/** 无需令牌。
                                - 媒体文件经 /media/** 直接访问；上传走 POST /api/seller/media，
                                  发布商品时回填返回的相对 URL。
                                """))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("token")));
    }
}
