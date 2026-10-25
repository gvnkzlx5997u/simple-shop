package com.simpleshop.history.entity;

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

import com.simpleshop.common.time.DatabaseTimeProvider;
import com.simpleshop.history.enums.GoodsHistoryResult;

/**
 * 历史商品。沿用原商品 ID；类别名、描述和图片都是归档时的快照。
 */
@Entity
@Table(name = "simpleshop_goods_history")
public class GoodsHistory {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    @Column(name = "category_l1_name", nullable = false, length = 50)
    private String categoryL1Name;

    @Column(name = "category_l2_name", nullable = false, length = 50)
    private String categoryL2Name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoodsHistoryResult result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "description_snapshot", nullable = false)
    private String descriptionSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "images_snapshot", nullable = false)
    private String imagesSnapshot;

    @Column(name = "create_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime createAt;

    @Column(name = "archived_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime archivedAt;

    protected GoodsHistory() {
    }

    public static GoodsHistory archive(
            String id,
            String name,
            BigDecimal price,
            int stock,
            String categoryL1Name,
            String categoryL2Name,
            String descriptionSnapshot,
            String imagesSnapshot,
            LocalDateTime createAt) {
        GoodsHistory value = new GoodsHistory();
        value.id = id;
        value.name = name;
        value.price = price;
        value.stock = stock;
        value.categoryL1Name = categoryL1Name;
        value.categoryL2Name = categoryL2Name;
        value.result = GoodsHistoryResult.SOLD_OUT;
        value.descriptionSnapshot = Objects.requireNonNull(descriptionSnapshot, "descriptionSnapshot");
        value.imagesSnapshot = Objects.requireNonNull(imagesSnapshot, "imagesSnapshot");
        value.createAt = Objects.requireNonNull(createAt, "createAt");
        value.archivedAt = DatabaseTimeProvider.utcNow();
        return value;
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

    public int getStock() {
        return stock;
    }

    public String getCategoryL1Name() {
        return categoryL1Name;
    }

    public String getCategoryL2Name() {
        return categoryL2Name;
    }

    public GoodsHistoryResult getResult() {
        return result;
    }

    public String getDescriptionSnapshot() {
        return descriptionSnapshot;
    }

    public String getImagesSnapshot() {
        return imagesSnapshot;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }
}
