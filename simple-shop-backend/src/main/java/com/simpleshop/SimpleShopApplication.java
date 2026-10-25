package com.simpleshop;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * 应用入口。
 *
 * <p>打包为可执行 war：既可用 {@code java -jar} 启动，也可部署到外部 Servlet 容器。
 * 应用只提供 REST 接口与媒体静态资源，不承载服务端渲染页面。
 *
 * <p>类加载时把 JVM 默认时区钉为 UTC。实体时间字段以 {@code @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)}
 * 字面量直传，本身与 JVM/会话时区无关；此处钉 UTC 是第二道防线：未来新增时间字段若漏标注，
 * 默认绑定在 JVM 与会话（connectionTimeZone=UTC）一致的时区下仍落成 UTC，日志时区也与部署容器对齐。
 * 回归测试 StorageTimeZoneTest。静态块在 main 与外部 Servlet 容器两种启动方式下都先于 Spring 上下文执行。
 */
@SpringBootApplication
public class SimpleShopApplication extends SpringBootServletInitializer {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(SimpleShopApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(SimpleShopApplication.class);
    }
}
