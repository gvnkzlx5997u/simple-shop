package com.simpleshop.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.order.enums.OrderStatus;

/**
 * 订单状态变更后的返回。推进与取消只回状态，明细与买家信息由订单详情接口提供。
 */
public record OrderStatusData(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("order_no") String orderNo,
        @JsonProperty("status") OrderStatus status,
        @JsonProperty("update_at") String updateAt) {
}
