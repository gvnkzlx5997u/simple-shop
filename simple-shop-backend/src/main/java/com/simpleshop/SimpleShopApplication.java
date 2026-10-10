package com.simpleshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * simple-shop 应用入口。
 *
 * <h2>当前阶段</h2>
 * <p>后端 Service 层 + 卖家端 REST 接口层（《后端业务层与卖家端接口层开发方案》阶段三），
 * 对外提供卖家端接口 {@code I11-01} ~ {@code I11-16}（第 11 章）。
 * 买家端 JSP 属<b>下一轮</b>范围，本阶段只提供其 Service，<b>不引入任何 JSP 依赖</b>。
 *
 * <h2>打包与启动方式（war）</h2>
 * <p>本工程打包为 <b>war</b>（见 {@code pom.xml}），以承载下一轮买家端 JSP
 * （{@code C-05}：买家端服务端渲染；JSP 需要 {@code WEB-INF} 下的 web 资源结构，jar 无法承载）。
 * 本类继承 {@link SpringBootServletInitializer}，因此<b>两种启动方式都能用</b>：
 * <table border="1">
 *   <caption>两种启动方式</caption>
 *   <tr><th>方式</th><th>命令/做法</th><th>走哪条代码路径</th></tr>
 *   <tr>
 *     <td><b>开发期（首选）</b></td>
 *     <td>{@code java -jar target/simple-shop.war}</td>
 *     <td>{@link #main(String[])} → 启动 embedded Tomcat（默认端口 8080）</td>
 *   </tr>
 *   <tr>
 *     <td>外部容器部署（备选）</td>
 *     <td>把 {@code simple-shop.war} 放进外部 Tomcat 的 {@code webapps/}</td>
 *     <td>容器调用 {@link #configure(SpringApplicationBuilder)}</td>
 *   </tr>
 * </table>
 *
 * <p><b>⚠️ 本轮有意保留 embedded Tomcat</b>（即 {@code spring-boot-starter-web} 的默认传递依赖，
 * 未设为 {@code provided}）：否则 {@code java -jar} 将因缺少 servlet 容器而<b>无法启动</b>，
 * 开发与自测会失去最方便的验证手段。
 * JSP 直出所需的 {@code tomcat-embed-jasper} 属<b>下一轮</b>依赖，本轮不加。
 * 若将来确定「只用外部容器、不再需要 java -jar」，再把 embedded Tomcat 改为
 * {@code provided} 并同时加上 {@code jakarta.servlet-api}(provided)。
 *
 * <h2>与数据层阶段的差异</h2>
 * <p>数据层阶段 {@code application.yml} 曾设 {@code spring.main.web-application-type=none}，
 * 上下文初始化完成（连库 → Flyway 迁移 → 实体与库结构校验）后正常退出、不占端口；
 * 该配置项<b>已在本阶段删除</b>。
 *
 * <p>注意 {@code spring.jpa.open-in-view=false}（§9.3）<b>保持</b>：
 * 因此 Service 层必须返回 DTO、不得把实体带出事务边界，否则会触发
 * {@code LazyInitializationException}（方案风险 R9）。
 */
@SpringBootApplication
public class SimpleShopApplication extends SpringBootServletInitializer {

    /**
     * 开发期入口：{@code java -jar target/simple-shop.war}。
     *
     * <p>启动 embedded Tomcat，无需任何外部容器。S2 的验证即走此路径。
     */
    public static void main(String[] args) {
        SpringApplication.run(SimpleShopApplication.class, args);
    }

    /**
     * 外部容器入口：Servlet 容器（Tomcat/Wildfly 等）部署 war 时调用本方法构建应用上下文。
     *
     * <p>本方法只声明「应用入口是哪个类」，不做任何额外定制；
     * 所有配置仍来自 {@code application.yml} 与各 {@code @Configuration} 类，
     * 因此「java -jar 启动」与「外部容器部署」两条路径的<b>行为一致</b>。
     */
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(SimpleShopApplication.class);
    }
}
