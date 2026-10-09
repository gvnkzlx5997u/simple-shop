package com.simpleshop;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.simpleshop.persistence.entity.Goods;
import com.simpleshop.persistence.enums.IntentionStatus;
import com.simpleshop.persistence.repository.GoodsRepository;
import com.simpleshop.persistence.repository.IntentionRepository;
import com.simpleshop.persistence.repository.ShopUserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 结构校验与唯一约束。
 *
 * <p>覆盖：
 * <ul>
 *   <li><b>结构校验</b>：{@code ddl-auto=validate} 启动通过（能启动即通过），
 *       并用 information_schema 核对表/列/约束确实如 §4 所建；</li>
 *   <li><b>唯一约束</b>：{@code token}、{@code queue_order}、{@code account} 重复插入应被数据库拒绝。</li>
 * </ul>
 */
@DisplayName("结构校验与唯一约束")
class StructureAndUniqueConstraintTest extends DataLayerTestBase {

    @Autowired private GoodsRepository goodsRepository;
    @Autowired private IntentionRepository intentionRepository;
    @Autowired private ShopUserRepository shopUserRepository;

    // -------------------------------------------------------------------------
    // 结构校验
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Flyway 已在测试库建出 7 张业务表（+ 1 张 schema 历史表）")
    void flywayCreatedAllTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = database()",
                String.class);

        assertThat(tables).contains(
                "simpleshop_users", "simpleshop_goods", "simpleshop_intentions",
                "simpleshop_goods_history", "simpleshop_intentions_history",
                "simpleshop_trade_history", "simpleshop_queue_sequence");
        assertThat(tables).contains("flyway_schema_history");

        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = 1", Integer.class);
        assertThat(applied).as("V1 + V2 两个迁移都应成功").isEqualTo(2);
    }

    @Test
    @DisplayName("所有表都是 InnoDB + utf8mb4_unicode_ci（§3.3）")
    void allTablesAreInnoDbWithUtf8mb4UnicodeCi() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select table_name, engine, table_collation from information_schema.tables "
                        + "where table_schema = database() and table_name like 'simpleshop%'");

        assertThat(rows).hasSize(7);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("ENGINE")).isEqualTo("InnoDB");
            assertThat(row.get("TABLE_COLLATION")).isEqualTo("utf8mb4_unicode_ci");
        });
    }

    @Test
    @DisplayName("历史意向表不含 queue_order 与 token 两列（DEC-DB-05）")
    void historyIntentionTableHasNoQueueOrderOrToken() {
        List<String> columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = database() and table_name = 'simpleshop_intentions_history' "
                        + "order by ordinal_position", String.class);

        assertThat(columns).containsExactly(
                "id", "fk_goods_history_id", "create_at", "name", "tel",
                "status", "fail_type", "fail_reason");
        assertThat(columns).doesNotContain("queue_order", "token");
    }

    @Test
    @DisplayName("只保留 4 条纯物理性 CHECK；枚举/格式类 CHECK 一律不存在（DEC-DB-14）")
    void onlyPhysicalCheckConstraintsExist() {
        List<String> checks = jdbcTemplate.queryForList(
                "select constraint_name from information_schema.table_constraints "
                        + "where table_schema = database() and constraint_type = 'CHECK' "
                        + "order by constraint_name", String.class);

        assertThat(checks).containsExactlyInAnyOrder(
                "ck_goods_history_time_order",
                "ck_goods_time_order",
                "ck_trade_history_time_order",
                "ck_user_password_not_blank");
    }

    @Test
    @DisplayName("外键只有 2 条，且流水表刻意无外键（9-N）")
    void foreignKeysAreExactlyTheTwoIntended() {
        List<String> fks = jdbcTemplate.queryForList(
                "select constraint_name from information_schema.table_constraints "
                        + "where table_schema = database() and constraint_type = 'FOREIGN KEY' "
                        + "order by constraint_name", String.class);

        assertThat(fks).containsExactlyInAnyOrder(
                "fkey_intention_history_fk_goods_history_id",
                "fkey_intentions_fk_good_id");
    }

    @Test
    @DisplayName("外键删除行为是 RESTRICT；索引集合与 §4 一致")
    void indexesAndDeleteRulesMatchDesign() {
        String deleteRule = jdbcTemplate.queryForObject(
                "select delete_rule from information_schema.referential_constraints "
                        + "where constraint_schema = database() "
                        + "and constraint_name = 'fkey_intentions_fk_good_id'", String.class);
        assertThat(deleteRule).isEqualTo("RESTRICT");

        // information_schema 在各平台/排序规则下的返回顺序不稳定（Windows 上 'PRIMARY' 排在 'idx_*' 之后），
        // 因此这里用「顺序无关」的集合断言，避免把平台的排序行为写成断言。
        List<String> goodsIndexes = jdbcTemplate.queryForList(
                "select distinct index_name from information_schema.statistics "
                        + "where table_schema = database() and table_name = 'simpleshop_goods'", String.class);
        // 本轮已按评审删掉 trade_end 索引
        assertThat(goodsIndexes).containsExactlyInAnyOrder(
                "PRIMARY", "idx_goods_create_at", "idx_goods_status");
        assertThat(goodsIndexes).doesNotContain("idx_goods_trade_end");

        List<String> intentionIndexes = jdbcTemplate.queryForList(
                "select distinct index_name from information_schema.statistics "
                        + "where table_schema = database() and table_name = 'simpleshop_intentions'", String.class);
        assertThat(intentionIndexes).containsExactlyInAnyOrder(
                "PRIMARY", "idx_intention_create_at", "idx_intention_goods_status_order",
                "idx_intention_status", "uk_intention_queue_order", "uk_intention_token");
    }

    @Test
    @DisplayName("V2 种子数据就位：单个卖家账号 + 队首序号行")
    void seedDataIsPresent() {
        assertThat(shopUserRepository.count()).as("单账号表应为 1 行").isEqualTo(1);
        assertThat(shopUserRepository.findByAccount("seller")).isPresent();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from simpleshop_queue_sequence where id = 1", Integer.class)).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // 唯一约束
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("queue_order 重复插入被数据库拒绝")
    void duplicateQueueOrderIsRejected() {
        Goods goods = goodsRepository.saveAndFlush(newGoods());
        intentionRepository.saveAndFlush(
                newIntention(goods, 501, IntentionStatus.queued, nextToken()));

        // 直接走 JDBC：JPA 的 save() 对「已存在主键」会退化为 merge（upsert），
        // 无法验证数据库约束；这里要验的是**数据库**会不会拦，故用原生 INSERT。
        assertRejectedByDatabase(() -> jdbcTemplate.update(
                "insert into simpleshop_intentions "
                        + "(id, fk_good_id, queue_order, create_at, name, tel, status, token) "
                        + "values (?,?,?,?,?,?,?,?)",
                TEST_MARKER + "ID1", goods.getId(), 501, utcNow(),
                TEST_MARKER + "买家", "13800000000", "queued", nextToken()),
                "uk_intention_queue_order");
    }

    @Test
    @DisplayName("token 重复插入被数据库拒绝")
    void duplicateTokenIsRejected() {
        Goods goods = goodsRepository.saveAndFlush(newGoods());
        String token = nextToken();
        intentionRepository.saveAndFlush(newIntention(goods, 502, IntentionStatus.queued, token));

        assertRejectedByDatabase(() -> jdbcTemplate.update(
                "insert into simpleshop_intentions "
                        + "(id, fk_good_id, queue_order, create_at, name, tel, status, token) "
                        + "values (?,?,?,?,?,?,?,?)",
                TEST_MARKER + "ID2", goods.getId(), 503, utcNow(),
                TEST_MARKER + "买家", "13800000000", "queued", token),
                "uk_intention_token");
    }

    @Test
    @DisplayName("account 重复插入被数据库拒绝")
    void duplicateAccountIsRejected() {
        assertRejectedByDatabase(() -> jdbcTemplate.update(
                "insert into simpleshop_users (id, account, password, create_at, update_at) "
                        + "values (?,?,?,?,?)",
                TEST_MARKER + "U1", "seller",
                "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ012", utcNow(), utcNow()),
                "uk_user_account");
    }

    @Test
    @DisplayName("主键重复插入被数据库拒绝")
    void duplicatePrimaryKeyIsRejected() {
        Goods goods = goodsRepository.saveAndFlush(newGoods());

        assertRejectedByDatabase(() -> jdbcTemplate.update(
                "insert into simpleshop_goods (id, name, price, status, create_at, update_at) "
                        + "values (?,?,?,?,?,?)",
                goods.getId(), TEST_MARKER + "重复主键", new BigDecimal("1.00"), "on_sale",
                utcNow(), utcNow()),
                "PRIMARY");
    }

    @Test
    @DisplayName("口令码为空串被 CHECK 拒绝（ck_user_password_not_blank）")
    void blankPasswordIsRejected() {
        assertRejectedByDatabase(() -> jdbcTemplate.update(
                "insert into simpleshop_users (id, account, password, create_at, update_at) "
                        + "values (?,?,?,?,?)",
                TEST_MARKER + "U2", TEST_MARKER + "blankpw", "", utcNow(), utcNow()),
                "ck_user_password_not_blank");
    }
}
