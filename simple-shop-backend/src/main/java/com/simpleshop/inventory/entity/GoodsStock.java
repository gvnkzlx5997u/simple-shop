package com.simpleshop.inventory.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.simpleshop.common.time.DatabaseTimeProvider;

/**
 * 商品库存。主键即商品 ID，与商品一对一并独立加锁，库存变更不更新商品行。
 */
@Entity
@Table(name = "simpleshop_goods_stock")
public class GoodsStock {

    @Id
    @Column(name = "fk_goods_id", length = 50)
    private String goodsId;

    @Column(nullable = false)
    private int available;

    @Column(name = "update_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime updateAt;

    protected GoodsStock() {
    }

    public static GoodsStock initialize(String goodsId, int available) {
        if (available < 1) {
            throw new IllegalArgumentException("initial stock must be at least 1");
        }
        GoodsStock value = new GoodsStock();
        value.goodsId = goodsId;
        value.available = available;
        return value;
    }

    public void setAvailable(int available) {
        if (available < 0) {
            throw new IllegalArgumentException("stock must not be negative");
        }
        this.available = available;
    }

    public int deductOne() {
        if (available < 1) {
            throw new IllegalStateException("stock is already zero for goods " + goodsId);
        }
        available--;
        return available;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updateAt = DatabaseTimeProvider.utcNow();
    }

    public String getGoodsId() {
        return goodsId;
    }

    public int getAvailable() {
        return available;
    }

    public LocalDateTime getUpdateAt() {
        return updateAt;
    }
}
