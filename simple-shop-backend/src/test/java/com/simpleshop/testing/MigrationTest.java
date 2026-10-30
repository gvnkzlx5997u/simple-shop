package com.simpleshop.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.account.entity.SellerAccount;
import com.simpleshop.category.entity.CategoryL1;
import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.entity.GoodsContentBlock;
import com.simpleshop.goods.entity.GoodsImage;
import com.simpleshop.history.entity.GoodsHistory;
import com.simpleshop.history.entity.IntentionHistory;
import com.simpleshop.intention.entity.GoodsQueueSequence;
import com.simpleshop.intention.entity.Intention;
import com.simpleshop.inventory.entity.GoodsStock;

/**
 * 空库迁移验证：在一次性 MySQL 里跑 V1→V2，再用 Hibernate 的 validate 校验实体与表结构。
 *
 * <p>默认在 Testcontainers 容器里执行；{@code -Plocal-mysql} 时在本机 MySQL 上创建
 * 一个随机命名的临时库，验证完立即删除，不碰 {@code simple_shop} / {@code simple_shop_test}。
 */
class MigrationTest {

    private static final List<Class<?>> ENTITIES = List.of(
            CategoryL1.class, CategoryL2.class,
            Goods.class, GoodsImage.class, GoodsContentBlock.class,
            GoodsStock.class,
            GoodsHistory.class, IntentionHistory.class,
            SellerAccount.class, BuyerAccount.class,
            GoodsQueueSequence.class, Intention.class);

    @Test
    void emptyDatabaseMigratesAndValidates() throws Exception {
        if (TestDatabase.usesContainer()) {
            MySQLContainer<?> mysql = TestDatabase.newMigrationContainer();
            try {
                mysql.start();
                verify(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            } finally {
                if (mysql.isRunning()) {
                    mysql.stop();
                }
            }
            return;
        }
        verifyLocalDatabase();
    }

    private static void verifyLocalDatabase() throws Exception {
        String testUrl = environment("TEST_DB_URL",
                "jdbc:mysql://localhost:3306/simple_shop_test");
        String user = environment("TEST_DB_USERNAME", environment("DB_USERNAME", "root"));
        String password = environment("TEST_DB_PASSWORD", environment("DB_PASSWORD", "123456"));

        int query = testUrl.indexOf('?');
        String base = query >= 0 ? testUrl.substring(0, query) : testUrl;
        String parameters = query >= 0 ? testUrl.substring(query) : "";
        int slash = base.lastIndexOf('/');
        if (slash < "jdbc:mysql://".length()) {
            throw new IllegalStateException("TEST_DB_URL has no database path: " + testUrl);
        }
        String server = base.substring(0, slash + 1);
        String database = "simple_shop_migration_" + UUID.randomUUID().toString().replace("-", "");
        String adminUrl = server + "mysql" + parameters;
        String targetUrl = server + database + parameters;

        try (Connection connection = DriverManager.getConnection(adminUrl, user, password);
                Statement statement = connection.createStatement()) {
            statement.execute("create database `" + database + "` "
                    + "character set utf8mb4 collate utf8mb4_unicode_ci");
        }
        try {
            verify(targetUrl, user, password);
        } finally {
            try (Connection connection = DriverManager.getConnection(adminUrl, user, password);
                    Statement statement = connection.createStatement()) {
                statement.execute("drop database if exists `" + database + "`");
            }
        }
    }

    private static void verify(String url, String user, String password) throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        assertThat(flyway.info().applied()).hasSize(2);
        assertSchema(url, user, password);
        validateWithHibernate(url, user, password);
    }

    private static void assertSchema(String url, String user, String password) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement statement = connection.createStatement()) {
            assertThat(count(statement,
                    "select count(*) from information_schema.tables "
                            + "where table_schema = database() and table_name like 'simpleshop_%'"))
                    .isEqualTo(12);
            assertThat(count(statement,
                    "select count(*) from information_schema.columns "
                            + "where table_schema = database() and table_name = 'simpleshop_goods' "
                            + "and column_name = 'stock'"))
                    .isZero();
            assertThat(count(statement,
                    "select count(*) from information_schema.tables "
                            + "where table_schema = database() and table_name = 'simpleshop_goods_stock'"))
                    .isEqualTo(1);
            assertThat(count(statement,
                    "select count(*) from simpleshop_seller_account where account = 'seller'"))
                    .isEqualTo(1);
            assertThat(count(statement,
                    "select count(*) from flyway_schema_history where success = 1"))
                    .isEqualTo(2);
        }
    }

    private static int count(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void validateWithHibernate(String url, String user, String password) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver")
                .applySetting("hibernate.connection.url", url)
                .applySetting("hibernate.connection.username", user)
                .applySetting("hibernate.connection.password", password)
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.hbm2ddl.auto", "validate")
                .build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            ENTITIES.forEach(sources::addAnnotatedClass);
            SessionFactory factory = sources.buildMetadata().buildSessionFactory();
            factory.close();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
