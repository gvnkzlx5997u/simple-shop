package com.simpleshop.payment.api;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 支付调用结果。三态对应 {@link PaymentStatus}，字段按状态取用：
 *
 * <ul>
 *   <li>{@code SUCCEEDED}：{@code paidAt} 与 {@code reference} 有值；</li>
 *   <li>{@code PENDING}：{@code redirectUrl} 有值，支付时间与流水号还没有；</li>
 *   <li>{@code FAILED}：其余全空。</li>
 * </ul>
 *
 * <p>失败不抛异常而是作为返回值，是因为「渠道说这次没成」是业务的正常分支，
 * 不是调用错误；订单侧要据此决定「留在待支付、允许重试」。
 */
public record PaymentResult(
        PaymentStatus status,
        PaymentChannel channel,
        LocalDateTime paidAt,
        String reference,
        String redirectUrl) {

    public PaymentResult {
        Objects.requireNonNull(status, "payment status");
        Objects.requireNonNull(channel, "payment channel");
    }

    public static PaymentResult succeeded(
            PaymentChannel channel, LocalDateTime paidAt, String reference) {
        Objects.requireNonNull(paidAt, "paid at");
        return new PaymentResult(PaymentStatus.SUCCEEDED, channel, paidAt, reference, null);
    }

    public static PaymentResult pending(PaymentChannel channel, String redirectUrl) {
        if (redirectUrl == null || redirectUrl.isBlank()) {
            throw new IllegalArgumentException("redirect url is required for a pending payment");
        }
        return new PaymentResult(PaymentStatus.PENDING, channel, null, null, redirectUrl);
    }

    public static PaymentResult failed(PaymentChannel channel) {
        return new PaymentResult(PaymentStatus.FAILED, channel, null, null, null);
    }
}
