package com.simpleshop.order.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * 提交订单。只收明细与数量，总额由服务端按商品价快照计算，不接受前端传金额。
 *
 * <p>同一订单内同一商品只允许出现一行；重复明细整单拒绝，不做数量合并。
 */
public record PlaceOrderRequest(
        @JsonProperty("items")
        @NotEmpty(message = "must not be empty")
        @Valid
        List<PlaceOrderItemRequest> items) {
}
