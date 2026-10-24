package com.simpleshop.goods.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.goods.enums.GoodsStatus;

/**
 * 商品列表项。列表只返回主图，没有主图时 main_image_url 为 null。
 */
public record GoodsSummaryData(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("stock") int stock,
        @JsonProperty("status") GoodsStatus status,
        @JsonProperty("main_image_url") String mainImageUrl,
        @JsonProperty("category") CategoryRefData category,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("update_at") String updateAt) {
}
