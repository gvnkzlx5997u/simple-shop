package com.simpleshop.cart.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.simpleshop.common.time.DatabaseTimeProvider;

/**
 * 购物车行：只存「买家 + 商品 + 加购时间」。
 *
 * <p>数量与勾选由前端 localStorage 维护，不落库；行没有状态，是否可选中由商品状态在读取时派生。
 * 「买家 + 商品」唯一由数据库唯一键保证，重复加购不新增行。
 */
@Entity
@Table(name = "simpleshop_cart_item")
public class CartItem {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "fk_buyer_id", nullable = false, length = 50)
    private String buyerId;

    @Column(name = "fk_goods_id", nullable = false, length = 50)
    private String goodsId;

    @Column(name = "create_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime createAt;

    protected CartItem() {
    }

    /**
     * 兜底：万一将来有人走 JPA {@code persist} 而不是原生 upsert，时间也不会为空。
     *
     * <p>当前加购一律走 {@code CartItemRepository.insertIfAbsent} 的原生 upsert，本回调
     * 不会被触发，{@code create_at} 由调用方显式传入。
     */
    @PrePersist
    void prePersist() {
        if (createAt == null) {
            createAt = DatabaseTimeProvider.utcNow();
        }
    }

    public String getId() {
        return id;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public String getGoodsId() {
        return goodsId;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }
}
