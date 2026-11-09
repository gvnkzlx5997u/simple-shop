package com.simpleshop.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 订单中的一条明细。数量由前端按购物车勾选提交，后端不校验商品是否在购物车中。
 */
public record PlaceOrderItemRequest(
        @JsonProperty("goods_id")
        @NotBlank(message = "must not be blank")
        @Size(max = 50, message = "must be at most 50 characters")
        String goodsId,

        @JsonProperty("quantity")
        @NotNull(message = "must not be null")
        @Min(value = 1, message = "must be at least 1")
        Integer quantity) {
}
