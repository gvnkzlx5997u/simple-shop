package com.simpleshop.order.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * 提交订单。只收收货地址与明细，总额由服务端按商品价快照计算，不接受前端传金额。
 *
 * <p>地址必须是买家地址簿里已有的条目：下单只做选择，不做新建或编辑。
 * 订单拿到的是地址快照，之后地址簿怎么改都不影响已下的订单。
 *
 * <p>同一订单内同一商品只允许出现一行；重复明细整单拒绝，不做数量合并。
 */
public record PlaceOrderRequest(
        @JsonProperty("address_id")
        @NotBlank(message = "must not be blank")
        String addressId,

        @JsonProperty("items")
        @NotEmpty(message = "must not be empty")
        @Valid
        List<PlaceOrderItemRequest> items) {
}
