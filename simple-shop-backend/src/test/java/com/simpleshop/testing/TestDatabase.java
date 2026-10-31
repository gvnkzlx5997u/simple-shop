package com.simpleshop.testing;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 测试数据库。
 *
 * <p>默认启动一次性 MySQL 容器；使用 {@code -Plocal-mysql} 时切换为
 * {@code application-test.yml} 配置的本机 {@code simple_shop_test}，不需要 Docker。
 * 容器只存在于测试 JVM，不参与构建产物或部署。
 */
public final class TestDatabase {

    /** 与部署使用的 MySQL 8.0 保持同一 tag，避免测试与生产版本漂移。 */
    public static final String IMAGE = "mysql:8.0";

    private static final boolean LOCAL = "local".equalsIgnoreCase(
            System.getProperty("simple-shop.test.database", "container"));

    private static MySQLContainer<?> mysql;

    private TestDatabase() {
    }

    /** 当前是否使用 Testcontainers；本地 profile 下为 {@code false}。 */
    public static boolean usesContainer() {
        return !LOCAL;
    }

    /** 把容器连接信息注入 Spring 测试上下文；本地 profile 下不注入，沿用 application-test.yml。 */
    public static synchronized void register(DynamicPropertyRegistry registry) {
        if (LOCAL) {
            return;
        }
        if (mysql == null) {
            mysql = start();
        }
        MySQLContainer<?> container = mysql;
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
        registry.add("spring.datasource.driver-class-name", container::getDriverClassName);
    }

    /** 迁移测试专用的空库容器；调用方负责 start/stop。 */
    public static MySQLContainer<?> newMigrationContainer() {
        return new MySQLContainer<>(DockerImageName.parse(IMAGE))
                .withDatabaseName("simple_shop_migration")
                .withUsername("test")
                .withPassword("test")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
    }

    private static MySQLContainer<?> start() {
        MySQLContainer<?> container = new MySQLContainer<>(DockerImageName.parse(IMAGE))
                .withDatabaseName("simple_shop_test")
                .withUsername("test")
                .withPassword("test")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
        try {
            container.start();
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "无法启动 Testcontainers MySQL（需要可用的 Docker）；"
                            + "没有 Docker 时请使用 mvn -Plocal-mysql ...",
                    exception);
        }
        return container;
    }
}
