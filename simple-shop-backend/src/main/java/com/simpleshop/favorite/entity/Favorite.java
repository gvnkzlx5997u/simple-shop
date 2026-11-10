package com.simpleshop.favorite.entity;

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
 * 收藏行：只存「买家 + 商品 + 收藏时间」，不做商品快照。
 *
 * <p>商品下架后行保留，展示时由商品状态派生「已下架/无货」；商品复售无需任何同步。
 * 「买家 + 商品」唯一由数据库唯一键保证，重复收藏不新增行。
 */
@Entity
@Table(name = "simpleshop_favorite")
public class Favorite {

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

    protected Favorite() {
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
