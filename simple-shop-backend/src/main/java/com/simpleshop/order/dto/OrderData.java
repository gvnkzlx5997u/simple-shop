package com.simpleshop.order.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.payment.api.PaymentChannel;

/**
 * 订单头：订单列表项与订单详情共用同一形状。
 *
 * <p>不含订单项：明细分页与列表分页是两件事，明细由 {@code /orders/{id}/items} 按订单 ID 单独查，
 * 先定订单、再取明细，避免一次返回里出现两层分页。
 *
 * <p>字段分四组：金额与状态、收货快照、支付与物流、退款汇总。收货三项是下单时的快照，
 * 地址簿之后怎么改都不会回写；{@code pay_deadline} 只在待支付时有值，前端拿它倒数，
 * 不自己按 24 小时算——时限是后端配置，前端算出来的只会和订单实际过期时间对不上。
 *
 * <p>{@code refunded_amount} 是两个来源的和：取消退款（支付包流水）+ 售后退款（售后单）。
 * 售后列表不内嵌在这里，按 {@code order_id} 单独查，理由同订单项。
 */
public record OrderData(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("order_no") String orderNo,
        @JsonProperty("status") OrderStatus status,
        @JsonProperty("total_amount") BigDecimal totalAmount,
        @JsonProperty("item_count") int itemCount,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("update_at") String updateAt,
        @JsonProperty("buyer") BuyerRefData buyer,
        @JsonProperty("receiver_name") String receiverName,
        @JsonProperty("receiver_phone") String receiverPhone,
        @JsonProperty("receiver_address") String receiverAddress,
        @JsonProperty("payment_channel") PaymentChannel paymentChannel,
        @JsonProperty("paid_at") String paidAt,
        @JsonProperty("pay_deadline") String payDeadline,
        @JsonProperty("carrier") String carrier,
        @JsonProperty("tracking_no") String trackingNo,
        @JsonProperty("logistics_remark") String logisticsRemark,
        @JsonProperty("refunded_amount") BigDecimal refundedAmount,
        @JsonProperty("aftersale_count") long aftersaleCount) {
}
