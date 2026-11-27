package com.simpleshop.payment.api;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 一次退款请求。本期只有「取消已支付订单」一种原因，退款金额是订单总额、渠道是原支付渠道。
 *
 * <p>{@code reason} 参与流水的唯一键：同一订单同一原因只退一次。新增原因（例如接真实渠道后的
 * 售后退款）要同时想清楚唯一键语义，不要复用 {@code ORDER_CANCEL}。
 */
public record RefundRequest(
        String orderId,
        String orderNo,
        BigDecimal amount,
        PaymentChannel channel,
        String reason) {

    public RefundRequest {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("order id is required");
        }
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("order no is required");
        }
        Objects.requireNonNull(channel, "payment channel");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("refund reason is required");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("refund amount must be positive");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("refund amount must have at most 2 decimals");
        }
        orderId = orderId.trim();
        orderNo = orderNo.trim();
        reason = reason.trim();
        amount = amount.setScale(2);
    }
}
