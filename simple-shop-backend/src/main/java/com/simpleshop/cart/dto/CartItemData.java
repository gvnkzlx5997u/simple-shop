package com.simpleshop.cart.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.enums.OffShelfReason;

/**
 * 购物车一行。数量与勾选不在这里：它们由前端 localStorage 维护。
 *
 * <p>{@code selectable} 等价于「商品在售」，由商品状态派生，不落库；库存不足仍可选中，
 * 由结算时校验拦截。已下架行保留展示，{@code off_shelf_reason} 供前端区分「已下架」与「无货」。
 */
public record CartItemData(
        @JsonProperty("goods_id") String goodsId,
        @JsonProperty("name") String name,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("stock") int stock,
        @JsonProperty("status") GoodsStatus status,
        @JsonProperty("off_shelf_reason") OffShelfReason offShelfReason,
        @JsonProperty("main_image_url") String mainImageUrl,
        @JsonProperty("selectable") boolean selectable,
        @JsonProperty("added_at") String addedAt) {
}
