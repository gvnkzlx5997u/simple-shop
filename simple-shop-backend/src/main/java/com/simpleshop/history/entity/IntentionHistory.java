package com.simpleshop.history.entity;

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
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.simpleshop.common.time.DatabaseTimeProvider;
import com.simpleshop.history.enums.IntentionResult;
import com.simpleshop.intention.enums.FailType;

/**
 * 历史意向。买家用户名、电话、商品名是归档时的字符串快照，不保存队列序号。
 */
@Entity
@Table(name = "simpleshop_intention_history")
public class IntentionHistory {

    @Id
    @Column(length = 50)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_goods_history_id", nullable = false)
    private GoodsHistory goodsHistory;

    @Column(name = "goods_name", nullable = false, length = 50)
    private String goodsName;

    @Column(name = "buyer_id", nullable = false, length = 50)
    private String buyerId;

    @Column(name = "buyer_username", nullable = false, length = 50)
    private String buyerUsername;

    @Column(name = "buyer_phone", nullable = false, length = 20)
    private String buyerPhone;

    @Column(name = "create_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime createAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntentionResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "fail_type", length = 20)
    private FailType failType;

    @Column(name = "finish_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime finishAt;

    @Column(name = "archived_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime archivedAt;

    protected IntentionHistory() {
    }

    public static IntentionHistory archive(
            String id,
            GoodsHistory goodsHistory,
            String goodsName,
            String buyerId,
            String buyerUsername,
            String buyerPhone,
            LocalDateTime createAt,
            IntentionResult result,
            FailType failType,
            LocalDateTime finishAt) {
        Objects.requireNonNull(result, "result");
        if (result == IntentionResult.FAILED && failType == null) {
            throw new IllegalArgumentException("failed intention must have failType");
        }
        if (result == IntentionResult.SUCCEEDED && failType != null) {
            throw new IllegalArgumentException("succeeded intention must not have failType");
        }
        IntentionHistory value = new IntentionHistory();
        value.id = id;
        value.goodsHistory = Objects.requireNonNull(goodsHistory, "goodsHistory");
        value.goodsName = Objects.requireNonNull(goodsName, "goodsName");
        value.buyerId = Objects.requireNonNull(buyerId, "buyerId");
        value.buyerUsername = Objects.requireNonNull(buyerUsername, "buyerUsername");
        value.buyerPhone = Objects.requireNonNull(buyerPhone, "buyerPhone");
        value.createAt = Objects.requireNonNull(createAt, "createAt");
        value.result = result;
        value.failType = failType;
        value.finishAt = Objects.requireNonNull(finishAt, "finishAt");
        value.archivedAt = DatabaseTimeProvider.utcNow();
        return value;
    }

    public String getId() {
        return id;
    }

    public GoodsHistory getGoodsHistory() {
        return goodsHistory;
    }

    public String getGoodsName() {
        return goodsName;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public String getBuyerUsername() {
        return buyerUsername;
    }

    public String getBuyerPhone() {
        return buyerPhone;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public IntentionResult getResult() {
        return result;
    }

    public FailType getFailType() {
        return failType;
    }

    public LocalDateTime getFinishAt() {
        return finishAt;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }
}
