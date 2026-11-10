package com.simpleshop.favorite.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.enums.OffShelfReason;

/**
 * 收藏一行。{@code detail_available} 等价于「商品在售」：已下架商品保留在列表里，
 * 但前端不提供详情跳转。
 */
public record FavoriteItemData(
        @JsonProperty("goods_id") String goodsId,
        @JsonProperty("name") String name,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("stock") int stock,
        @JsonProperty("status") GoodsStatus status,
        @JsonProperty("off_shelf_reason") OffShelfReason offShelfReason,
        @JsonProperty("main_image_url") String mainImageUrl,
        @JsonProperty("detail_available") boolean detailAvailable,
        @JsonProperty("favorited_at") String favoritedAt) {
}
