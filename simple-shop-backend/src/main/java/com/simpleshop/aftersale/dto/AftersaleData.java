package com.simpleshop.aftersale.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.aftersale.enums.AftersaleResult;
import com.simpleshop.aftersale.enums.AftersaleStatus;

/**
 * 售后单摘要：列表与详情共用，不含描述与图片——这两项按需在详情里给。
 *
 * <p>{@code goods_name}、{@code order_no} 来自订单项快照，不在这里另存一份：
 * 售后单只记「针对哪个订单项」，商品名与订单号以订单为准。
 */
public record AftersaleData(
        @JsonProperty("aftersale_id") String aftersaleId,
        @JsonProperty("aftersale_no") String aftersaleNo,
        @JsonProperty("order_id") String orderId,
        @JsonProperty("order_no") String orderNo,
        @JsonProperty("order_item_id") String orderItemId,
        @JsonProperty("goods_name") String goodsName,
        @JsonProperty("title") String title,
        @JsonProperty("status") AftersaleStatus status,
        @JsonProperty("result") AftersaleResult result,
        @JsonProperty("refund_amount") BigDecimal refundAmount,
        @JsonProperty("refunded_at") String refundedAt,
        @JsonProperty("create_at") String createAt) {
}
