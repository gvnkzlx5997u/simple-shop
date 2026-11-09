package com.simpleshop.order.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.order.enums.OrderStatus;

/**
 * 下单成功的返回。订单详情由订单详情接口提供，这里只回创建结果，避免两套明细结构。
 */
public record OrderPlacedData(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("order_no") String orderNo,
        @JsonProperty("total_amount") BigDecimal totalAmount,
        @JsonProperty("status") OrderStatus status,
        @JsonProperty("create_at") String createAt) {
}
