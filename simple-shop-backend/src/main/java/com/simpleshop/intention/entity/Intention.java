package com.simpleshop.intention.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.common.id.Ids;
import com.simpleshop.common.time.DatabaseTimeProvider;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.intention.enums.FailType;
import com.simpleshop.intention.enums.IntentionStatus;

/**
 * 当前意向。只引用买家账号，不保存用户名和电话。
 */
@Entity
@Table(name = "simpleshop_intention",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_intention_goods_queue_order",
               columnNames = {"fk_goods_id", "queue_order"}))
public class Intention {

    @Id
    @Column(length = 50)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_goods_id", nullable = false)
    private Goods goods;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_buyer_id", nullable = false)
    private BuyerAccount buyer;

    @Column(name = "queue_order", nullable = false)
    private long queueOrder;

    @Column(name = "create_at", nullable = false)
    private LocalDateTime createAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntentionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "fail_type", length = 20)
    private FailType failType;

    @Column(name = "finish_at")
    private LocalDateTime finishAt;

    protected Intention() {
    }

    public static Intention submit(Goods goods, BuyerAccount buyer, long queueOrder) {
        if (queueOrder < 1) {
            throw new IllegalArgumentException("queue_order must be at least 1");
        }
        Intention value = new Intention();
        value.id = Ids.next("I");
        value.goods = goods;
        value.buyer = buyer;
        value.queueOrder = queueOrder;
        value.createAt = DatabaseTimeProvider.utcNow();
        value.status = IntentionStatus.QUEUED;
        return value;
    }

    public void enterTrade() {
        status = IntentionStatus.TRADING;
        failType = null;
        finishAt = null;
    }

    public void markSucceeded() {
        status = IntentionStatus.SUCCEEDED;
        failType = null;
        finishAt = DatabaseTimeProvider.utcNow();
    }

    public void markFailed(FailType failType) {
        this.failType = Objects.requireNonNull(failType, "failType");
        status = IntentionStatus.FAILED;
        finishAt = DatabaseTimeProvider.utcNow();
    }

    public void requeue(long queueOrder) {
        if (queueOrder < 1) {
            throw new IllegalArgumentException("queue_order must be at least 1");
        }
        this.queueOrder = queueOrder;
        status = IntentionStatus.QUEUED;
        failType = null;
        finishAt = null;
    }

    @PrePersist
    void prePersist() {
        if (createAt == null) {
            createAt = DatabaseTimeProvider.utcNow();
        }
    }

    public String getId() {
        return id;
    }

    public Goods getGoods() {
        return goods;
    }

    public BuyerAccount getBuyer() {
        return buyer;
    }

    public long getQueueOrder() {
        return queueOrder;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public IntentionStatus getStatus() {
        return status;
    }

    public FailType getFailType() {
        return failType;
    }

    public LocalDateTime getFinishAt() {
        return finishAt;
    }
}
