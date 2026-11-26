package com.simpleshop.payment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.simpleshop.common.id.Ids;
import com.simpleshop.common.text.TextRules;
import com.simpleshop.payment.api.PaymentChannel;

/**
 * 退款流水：只记「取消已支付订单」的整单退款。
 *
 * <p>售后退款不写这里——它按订单项退，字段记在售后单上。两条来源分开记，
 * 订单详情的「已退款」再聚合，避免同一笔钱在两个地方各存一份。
 *
 * <p>order_no 是快照：支付包不读订单表，账要能独立看懂，对账时不必回查订单。
 */
@Entity
@Table(name = "simpleshop_payment_refund")
public class PaymentRefund {

    /** 本期唯一原因：整单取消。新增原因要同时改唯一键语义，不要随手复用。 */
    public static final String REASON_ORDER_CANCEL = "ORDER_CANCEL";

    private static final int ORDER_NO_MAX = 32;

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "fk_order_id", nullable = false, length = 50)
    private String orderId;

    @Column(name = "order_no", nullable = false, length = ORDER_NO_MAX)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentChannel channel;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String reason;

    @Column(name = "refunded_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime refundedAt;

    protected PaymentRefund() {
    }

    /**
     * 取消退款：金额是订单总额，渠道是订单原支付渠道——「原路退回」就是把这两个值原样带回来。
     */
    public static PaymentRefund forOrderCancel(
            String orderId,
            String orderNo,
            PaymentChannel channel,
            BigDecimal amount,
            LocalDateTime refundedAt) {
        Objects.requireNonNull(channel, "payment channel");
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("refund amount must be positive");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("refund amount must have at most 2 decimals");
        }
        Objects.requireNonNull(refundedAt, "refunded at");

        PaymentRefund refund = new PaymentRefund();
        refund.id = Ids.next("RF");
        refund.orderId = requireText(orderId, 50, "order id");
        refund.orderNo = requireText(orderNo, ORDER_NO_MAX, "order no");
        refund.channel = channel;
        refund.amount = amount.setScale(2);
        refund.reason = REASON_ORDER_CANCEL;
        refund.refundedAt = refundedAt;
        return refund;
    }

    private static String requireText(String raw, int maxLength, String field) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String value = raw.trim();
        if (TextRules.charCount(value) > maxLength) {
            throw new IllegalArgumentException(
                    field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }

    public String getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public PaymentChannel getChannel() {
        return channel;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }
}
