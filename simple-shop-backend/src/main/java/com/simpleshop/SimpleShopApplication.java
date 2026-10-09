package com.simpleshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * simple-shop 应用入口。
 *
 * <p>本阶段范围仅「数据库 / entity / repository」，
 * <b>不含 Service、Controller、页面</b>。因此以非 web 方式启动
 * （{@code spring.main.web-application-type=none}，见 {@code application.yml}）：
 * 应用完成 Spring 上下文初始化（连接数据源 → 执行 Flyway 迁移 → 校验实体与库结构）后正常退出，
 * 不占用 HTTP 端口。这与 {@code spring.jpa.open-in-view=false}（§9.3）的取舍一致。
 *
 * <p>后续加入 web 层时，把 {@code application.yml} 中的
 * {@code spring.main.web-application-type} 去掉（或改为 {@code servlet}）即可，
 * 本类无需改动。
 */
@SpringBootApplication
public class SimpleShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleShopApplication.class, args);
    }
}
