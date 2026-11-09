package com.simpleshop.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 订单里的买家引用：只回 ID 与实时用户名，不带电话等账号字段。
 */
public record BuyerRefData(
        @JsonProperty("id") String id,
        @JsonProperty("username") String username) {
}
