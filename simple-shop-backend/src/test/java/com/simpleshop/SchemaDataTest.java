package com.simpleshop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SchemaDataTest extends DataLayerTestBase {

    @Test
    @DisplayName("基线脚本创建了全部商品域表")
    void baselineCreatesAllTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = database()",
                String.class);

        assertThat(tables).contains(
                "simpleshop_category_l1",
                "simpleshop_category_l2",
                "simpleshop_seller_account",
                "simpleshop_buyer_account",
                "simpleshop_goods",
                "simpleshop_goods_image",
                "simpleshop_goods_content_block",
                "simpleshop_goods_history",
                "simpleshop_goods_queue_sequence",
                "simpleshop_intention",
                "simpleshop_intention_history");
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
    @DisplayName("历史商品与历史意向的快照列是 JSON")
    void snapshotColumnsAreJson() {
        String goodsJson = jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_schema = database() "
                        + "and table_name = 'simpleshop_goods_history' "
                        + "and column_name = 'description_snapshot'",
                String.class);
        String intentionJson = jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_schema = database() "
                        + "and table_name = 'simpleshop_goods_history' "
                        + "and column_name = 'images_snapshot'",
                String.class);

        assertThat(goodsJson).isEqualTo("json");
        assertThat(intentionJson).isEqualTo("json");
    }

    @Test
    @DisplayName("种子数据只有一条卖家账号")
    void seedContainsSingleSeller() {
        Integer sellers = jdbcTemplate.queryForObject(
                "select count(*) from simpleshop_seller_account", Integer.class);

        assertThat(sellers).isEqualTo(1);
    }
}
