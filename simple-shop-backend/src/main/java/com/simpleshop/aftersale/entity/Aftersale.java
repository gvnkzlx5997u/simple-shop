package com.simpleshop.aftersale.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.simpleshop.aftersale.enums.AftersaleResult;
import com.simpleshop.aftersale.enums.AftersaleStatus;
import com.simpleshop.common.id.Ids;
import com.simpleshop.common.text.TextRules;
import com.simpleshop.common.time.DatabaseTimeProvider;

/**
 * 售后单。一张单只对应一个订单项，整行全退，不记退货数量。
 *
 * <p>状态与结果强绑定：未完成时不能有结果，完成时必须有结果；退款类结果必须同时有金额与
 * 退款时间，换货两者都为空。这套约束在库里有 CHECK 兜底，这里再断言一次，
 * 让非法状态在内存里就停住，而不是等提交时才变成约束异常。
 *
 * <p>退款金额由服务层按订单项快照算好传进来，实体只负责校验形状：
 * 「多少钱」是业务计算，「能不能写」是状态规则，两者不混在一起。
 */
@Entity
@Table(name = "simpleshop_aftersale")
public class Aftersale {

    private static final int TITLE_MAX = 100;
    private static final int DESCRIPTION_MAX = 1000;

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "aftersale_no", nullable = false, unique = true, length = 32)
    private String aftersaleNo;

    @Column(name = "fk_order_id", nullable = false, length = 50)
    private String orderId;

    /** 目标订单项，不是商品：同一商品可能在多个订单项里各申请一次。 */
    @Column(name = "fk_order_item_id", nullable = false, length = 50)
    private String orderItemId;

    /** 冗余自订单，供买家「我的售后」直接按买家分页，避免每次 join 订单。 */
    @Column(name = "fk_buyer_id", nullable = false, length = 50)
    private String buyerId;

    @Column(nullable = false, length = TITLE_MAX)
    private String title;

    @Column(nullable = false, length = DESCRIPTION_MAX)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AftersaleStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AftersaleResult result;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refunded_at")
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime refundedAt;

    @Column(name = "create_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime createAt;

    @Column(name = "update_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime updateAt;

    protected Aftersale() {
    }

    /** 买家提交申请：只收标题、描述与图片（图片另行落库），申请时没有「售后类型」。 */
    public static Aftersale apply(
            String aftersaleNo,
            String orderId,
            String orderItemId,
            String buyerId,
            String title,
            String description) {
        Aftersale value = new Aftersale();
        value.id = Ids.next("AS");
        value.aftersaleNo = requireText(aftersaleNo, 32, "aftersale no");
        value.orderId = requireText(orderId, 50, "order id");
        value.orderItemId = requireText(orderItemId, 50, "order item id");
        value.buyerId = requireText(buyerId, 50, "buyer id");
        value.title = requireText(title, TITLE_MAX, "title");
        value.description = requireDescription(description);
        value.status = AftersaleStatus.PENDING;
        return value;
    }

    /**
     * 卖家登记完成结果。退款类结果必须带金额与时间；换货与「未退款」必须两者皆空。
     *
     * <p>退款金额不允许服务层漏传：宁可在这里抛错，也不要落一条「已完成、却没退钱」的单。
     */
    public void complete(
            AftersaleResult result, BigDecimal refundAmount, LocalDateTime refundedAt) {
        requireStatus(AftersaleStatus.PENDING, "complete");
        Objects.requireNonNull(result, "aftersale result");
        if (result.producesRefund()) {
            if (refundAmount == null || refundAmount.signum() <= 0) {
                throw new IllegalArgumentException("refund amount must be positive");
            }
            if (refundAmount.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException("refund amount must have at most 2 decimals");
            }
            if (refundedAt == null) {
                throw new IllegalArgumentException("refunded at is required");
            }
            this.refundAmount = refundAmount.setScale(2);
            this.refundedAt = refundedAt;
        } else {
            if (refundAmount != null || refundedAt != null) {
                throw new IllegalArgumentException(
                        "exchange must not carry refund amount or refunded time");
            }
            this.refundAmount = null;
            this.refundedAt = null;
        }
        this.result = result;
        this.status = AftersaleStatus.COMPLETED;
    }

    /** 卖家取消（与拒绝同义）：只对未处理的单有效，不产生退款。 */
    public void cancel() {
        requireStatus(AftersaleStatus.PENDING, "cancel");
        this.status = AftersaleStatus.CANCELED;
    }

    public boolean isPending() {
        return status == AftersaleStatus.PENDING;
    }

    /** 是否「已产生退款」：只有完成且结果为退款类才算，它决定该订单项的退款额度是否被占用。 */
    public boolean hasRefunded() {
        return status == AftersaleStatus.COMPLETED
                && result != null
                && result.producesRefund();
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
        if (TextRules.containsAngleBracket(value)) {
            throw new IllegalArgumentException(field + " must not contain angle brackets");
        }
        return value;
    }

    /** 描述允许多行：买家常把情况分几条写，换行是正常内容，不当作非法字符。 */
    private static String requireDescription(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
        String value = raw.trim();
        if (TextRules.charCount(value) > DESCRIPTION_MAX) {
            throw new IllegalArgumentException(
                    "description must not exceed " + DESCRIPTION_MAX + " characters");
        }
        if (TextRules.containsAngleBracket(value)) {
            throw new IllegalArgumentException("description must not contain angle brackets");
        }
        if (TextRules.containsDisallowedControlChar(value)) {
            throw new IllegalArgumentException(
                    "description must not contain control characters");
        }
        return value;
    }

    private void requireStatus(AftersaleStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                    "cannot " + action + " an aftersale in status " + status);
        }
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = DatabaseTimeProvider.utcNow();
        if (createAt == null) {
            createAt = now;
        }
        if (updateAt == null) {
            updateAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updateAt = DatabaseTimeProvider.utcNow();
    }

    public String getId() {
        return id;
    }

    public String getAftersaleNo() {
        return aftersaleNo;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getOrderItemId() {
        return orderItemId;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public AftersaleStatus getStatus() {
        return status;
    }

    public AftersaleResult getResult() {
        return result;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public LocalDateTime getUpdateAt() {
        return updateAt;
    }
}
