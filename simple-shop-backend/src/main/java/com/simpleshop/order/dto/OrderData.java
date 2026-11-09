package com.simpleshop.order.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.order.enums.OrderStatus;

/**
 * 订单头：订单列表项与订单详情共用同一形状。
 *
 * <p>不含订单项：明细分页与列表分页是两件事，明细由 {@code /orders/{id}/items} 按订单 ID 单独查，
 * 先定订单、再取明细，避免一次返回里出现两层分页。
 */
public record OrderData(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("order_no") String orderNo,
        @JsonProperty("status") OrderStatus status,
        @JsonProperty("total_amount") BigDecimal totalAmount,
        @JsonProperty("item_count") int itemCount,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("update_at") String updateAt,
        @JsonProperty("buyer") BuyerRefData buyer) {
}
