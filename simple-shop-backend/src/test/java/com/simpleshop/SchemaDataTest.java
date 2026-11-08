package com.simpleshop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simpleshop.testing.DataLayerTestBase;

class SchemaDataTest extends DataLayerTestBase {

    @Test
    @DisplayName("迁移到 V3 后正好 12 张业务表：意向与归档已下线，交易四表已建")
    void migratesToTwelveTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = database() and table_name like 'simpleshop\\_%'",
                String.class);

        assertThat(tables).containsExactlyInAnyOrder(
                "simpleshop_category_l1",
                "simpleshop_category_l2",
                "simpleshop_seller_account",
                "simpleshop_buyer_account",
                "simpleshop_goods",
                "simpleshop_goods_stock",
                "simpleshop_goods_image",
                "simpleshop_goods_content_block",
                "simpleshop_order",
                "simpleshop_order_item",
                "simpleshop_cart_item",
                "simpleshop_favorite");
        assertThat(tables).doesNotContain(
                "simpleshop_intention",
                "simpleshop_intention_history",
                "simpleshop_goods_history",
                "simpleshop_goods_queue_sequence");
    }

    @Test
    @DisplayName("库存已拆到独立表，商品表不再有 stock 列")
    void stockIsSeparateTable() {
        Long stockColumn = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = database() "
                        + "and table_name = 'simpleshop_goods' and column_name = 'stock'",
                Long.class);
        assertThat(stockColumn).isZero();

        Long availableColumn = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = database() "
                        + "and table_name = 'simpleshop_goods_stock' and column_name = 'available'",
                Long.class);
        assertThat(availableColumn).isEqualTo(1);
    }

    @Test
    @DisplayName("商品表的下架原因与时间可空，状态仍有取值约束")
    void goodsOffShelfColumnsAreNullable() {
        List<String> nullable = jdbcTemplate.queryForList(
                "select concat(column_name, ':', is_nullable) from information_schema.columns "
                        + "where table_schema = database() and table_name = 'simpleshop_goods' "
                        + "and column_name in ('off_shelf_reason', 'off_shelf_at')",
                String.class);
        assertThat(nullable).containsExactlyInAnyOrder(
                "off_shelf_reason:YES", "off_shelf_at:YES");

        Long checks = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.table_constraints "
                        + "where table_schema = database() and table_name = 'simpleshop_goods' "
                        + "and constraint_type = 'CHECK' "
                        + "and constraint_name in ('ck_goods_status', 'ck_goods_off_shelf')",
                Long.class);
        assertThat(checks).isEqualTo(2);
    }

    @Test
    @DisplayName("描述块文本列宽 1000，主图唯一辅助列为生成列")
    void contentBlockAndMainImageColumnsMatchDesign() {
        Long textLength = jdbcTemplate.queryForObject(
                "select character_maximum_length from information_schema.columns "
                        + "where table_schema = database() "
                        + "and table_name = 'simpleshop_goods_content_block' "
                        + "and column_name = 'text_content'",
                Long.class);
        assertThat(textLength).isEqualTo(1000);

        String extra = jdbcTemplate.queryForObject(
                "select extra from information_schema.columns "
                        + "where table_schema = database() "
                        + "and table_name = 'simpleshop_goods_image' "
                        + "and column_name = 'main_flag'",
                String.class);
        assertThat(extra).containsIgnoringCase("GENERATED");
    }

    @Test
    @DisplayName("订单金额两位小数，订单号列宽 32")
    void orderMoneyAndNumberColumns() {
        String amount = jdbcTemplate.queryForObject(
                "select concat(data_type, '(', numeric_precision, ',', numeric_scale, ')') "
                        + "from information_schema.columns "
                        + "where table_schema = database() "
                        + "and table_name = 'simpleshop_order' and column_name = 'total_amount'",
                String.class);
        assertThat(amount).isEqualTo("decimal(12,2)");

        String unitPrice = jdbcTemplate.queryForObject(
                "select concat(data_type, '(', numeric_precision, ',', numeric_scale, ')') "
                        + "from information_schema.columns "
                        + "where table_schema = database() "
                        + "and table_name = 'simpleshop_order_item' and column_name = 'unit_price'",
                String.class);
        assertThat(unitPrice).isEqualTo("decimal(8,2)");

        Long orderNoLength = jdbcTemplate.queryForObject(
                "select character_maximum_length from information_schema.columns "
                        + "where table_schema = database() "
                        + "and table_name = 'simpleshop_order' and column_name = 'order_no'",
                Long.class);
        assertThat(orderNoLength).isEqualTo(32);
    }

    @Test
    @DisplayName("种子数据只有一条卖家账号")
    void seedContainsSingleSeller() {
        Integer sellers = jdbcTemplate.queryForObject(
                "select count(*) from simpleshop_seller_account", Integer.class);

        assertThat(sellers).isEqualTo(1);
    }
}
