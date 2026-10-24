package com.simpleshop.history.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 历史商品列表条目。类别名为归档时的快照。
 */
public record GoodsHistorySummaryData(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("category_l1_name") String categoryL1Name,
        @JsonProperty("category_l2_name") String categoryL2Name,
        @JsonProperty("result") String result,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("archived_at") String archivedAt) {
}
