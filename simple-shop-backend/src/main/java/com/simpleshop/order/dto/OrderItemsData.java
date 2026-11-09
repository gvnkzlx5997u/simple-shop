package com.simpleshop.order.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 一笔订单的全部明细。订单项没有状态、数量有限，不分页。
 */
public record OrderItemsData(
        @JsonProperty("items") List<OrderItemData> items) {

    public OrderItemsData {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
