package com.simpleshop.cart.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 购物车列表。不分页：前端要算总价与导航角标，翻页会让合计漏行。
 */
public record CartListData(
        @JsonProperty("items") List<CartItemData> items) {

    public CartListData {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
