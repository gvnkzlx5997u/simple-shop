package com.simpleshop.cart.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 购物车多件转收藏：入参为商品 ID 集合，整批成功或整批拒绝。 */
public record MoveToFavoritesRequest(
        @JsonProperty("goods_ids") @NotEmpty List<String> goodsIds) {
}
