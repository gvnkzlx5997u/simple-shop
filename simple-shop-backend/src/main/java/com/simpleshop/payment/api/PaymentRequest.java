package com.simpleshop.payment.api;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 一次支付请求。
 *
 * <p>字段全部由订单侧准备好：支付包不读订单表，也就不需要知道订单长什么样。
 * {@code orderNo} 是渠道侧的业务单号，真实网关靠它做幂等与对账。
 */
public record PaymentRequest(
        String orderId,
        String orderNo,
        BigDecimal amount,
        PaymentChannel channel) {

    public PaymentRequest {
        orderId = requireText(orderId, "order id");
        orderNo = requireText(orderNo, "order no");
        Objects.requireNonNull(channel, "payment channel");
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("payment amount must be positive");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("payment amount must have at most 2 decimals");
        }
        amount = amount.setScale(2);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
