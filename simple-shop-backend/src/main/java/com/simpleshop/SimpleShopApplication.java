package com.simpleshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * 应用入口。
 *
 * <p>打包为可执行 war：既可用 {@code java -jar} 启动，也可部署到外部 Servlet 容器。
 * 应用只提供 REST 接口与图片静态资源，不承载服务端渲染页面。
 */
@SpringBootApplication
public class SimpleShopApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(SimpleShopApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(SimpleShopApplication.class);
    }
}
