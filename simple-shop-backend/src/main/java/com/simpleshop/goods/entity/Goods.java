package com.simpleshop.goods.entity;

import java.math.BigDecimal;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.common.id.Ids;
import com.simpleshop.common.time.DatabaseTimeProvider;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.enums.OffShelfReason;

/**
 * 商品。描述文本、图片和视频分别由子表保存，库存由独立的库存聚合持有。
 *
 * <p>状态与下架原因必须成对变化，因此只暴露 {@link #offShelf(OffShelfReason)} 与
 * {@link #backOnSale()} 两个入口，不提供单独写入 status 或 offShelfReason 的方法。
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

    @Enumerated(EnumType.STRING)
    @Column(name = "off_shelf_reason", length = 20)
    private OffShelfReason offShelfReason;

    @Column(name = "off_shelf_at")
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime offShelfAt;

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

    /**
     * 下架：状态、原因与时间同时写入。只在售商品可以下架，调用方负责业务分支。
     */
    public void offShelf(OffShelfReason reason) {
        if (status != GoodsStatus.ON_SALE) {
            throw new IllegalStateException("only on-sale goods can go off shelf");
        }
        status = GoodsStatus.OFF_SHELF;
        offShelfReason = Objects.requireNonNull(reason, "reason");
        offShelfAt = DatabaseTimeProvider.utcNow();
    }

    /**
     * 自动上架：清空原因与下架时间。手动下架是终态，不允许借此恢复。
     */
    public void backOnSale() {
        if (status != GoodsStatus.OFF_SHELF || !offShelfReason.restockable()) {
            throw new IllegalStateException("goods cannot be put back on sale");
        }
        status = GoodsStatus.ON_SALE;
        offShelfReason = null;
        offShelfAt = null;
    }

    public void changeCategory(CategoryL2 category) {
        this.category = category;
    }

    public boolean isOnSale() {
        return status == GoodsStatus.ON_SALE;
    }

    /** 库存耗尽（非手动）下架的商品，补货后可以自动复售。 */
    public boolean isRestockable() {
        return status == GoodsStatus.OFF_SHELF && offShelfReason.restockable();
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

    public OffShelfReason getOffShelfReason() {
        return offShelfReason;
    }

    public LocalDateTime getOffShelfAt() {
        return offShelfAt;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public LocalDateTime getUpdateAt() {
        return updateAt;
    }
}
