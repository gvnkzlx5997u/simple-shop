package com.simpleshop.order.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 订单项：商品引用、下单快照与数量，外加服务端算好的小计。
 *
 * <p>小计由后端给，避免前端用浮点数乘出 299.70000000000005 这类展示问题。
 */
public record OrderItemData(
        @JsonProperty("goods_id") String goodsId,
        @JsonProperty("goods_name") String goodsName,
        @JsonProperty("unit_price") BigDecimal unitPrice,
        @JsonProperty("quantity") int quantity,
        @JsonProperty("amount") BigDecimal amount) {
}
