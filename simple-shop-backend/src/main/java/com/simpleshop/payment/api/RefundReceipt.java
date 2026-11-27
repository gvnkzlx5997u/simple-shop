package com.simpleshop.payment.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款回执。退款是即时口径：拿到回执就意味着已原路退回，没有「退款中」。
 */
public record RefundReceipt(
        String refundId,
        BigDecimal amount,
        LocalDateTime refundedAt) {
}
