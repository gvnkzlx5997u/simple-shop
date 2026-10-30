package com.simpleshop.goods.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.common.id.Ids;
import com.simpleshop.common.time.DatabaseTimeProvider;
import com.simpleshop.goods.enums.GoodsStatus;

/**
 * 当前商品。描述文本、图片和视频分别由子表保存。
 */
@Entity
@Table(name = "simpleshop_goods")
public class Goods {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_category_id", nullable = false)
    private CategoryL2 category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoodsStatus status;

    @Column(name = "create_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime createAt;

    @Column(name = "update_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime updateAt;

    protected Goods() {
    }

    public static Goods publish(String name, BigDecimal price, CategoryL2 category) {
        Goods value = new Goods();
        value.id = Ids.next("G");
        value.name = name;
        value.price = price;
        value.category = category;
        value.status = GoodsStatus.ON_SALE;
        return value;
    }

    public void changeStatus(GoodsStatus status) {
        this.status = status;
    }

    public void changeCategory(CategoryL2 category) {
        this.category = category;
    }

    public boolean isOnSale() {
        return status == GoodsStatus.ON_SALE;
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

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public CategoryL2 getCategory() {
        return category;
    }

    public GoodsStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public LocalDateTime getUpdateAt() {
        return updateAt;
    }
}
