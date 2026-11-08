package com.simpleshop.goods.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.enums.OffShelfReason;

/**
 * 商品列表项。列表只返回主图，没有主图时 main_image_url 为 null。
 * 在售时 off_shelf_reason 与 off_shelf_at 均为 null。
 */
public record GoodsSummaryData(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("stock") int stock,
        @JsonProperty("status") GoodsStatus status,
        @JsonProperty("off_shelf_reason") OffShelfReason offShelfReason,
        @JsonProperty("off_shelf_at") String offShelfAt,
        @JsonProperty("main_image_url") String mainImageUrl,
        @JsonProperty("category") CategoryRefData category,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("update_at") String updateAt) {
}
