package com.simpleshop.goods.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.simpleshop.common.id.Ids;
import com.simpleshop.common.time.DatabaseTimeProvider;

/**
 * 商品主图或图集图片。只保存图片，不保存视频。
 *
 * <p>{@code is_main} 用 byte 映射 tinyint，{@code main_flag} 是数据库生成列。
 */
@Entity
@Table(name = "simpleshop_goods_image",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_goods_image_sort", columnNames = {"fk_goods_id", "sort_no"}))
public class GoodsImage {

    @Id
    @Column(length = 50)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_goods_id", nullable = false)
    private Goods goods;

    @Column(nullable = false, length = 512)
    private String url;

    @Column(name = "is_main", nullable = false)
    private byte isMain;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;

    @Column(name = "create_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime createAt;

    protected GoodsImage() {
    }

    public static GoodsImage add(Goods goods, String url, boolean main, int sortNo) {
        if (sortNo < 1) {
            throw new IllegalArgumentException("sort_no must be at least 1");
        }
        GoodsImage value = new GoodsImage();
        value.id = Ids.next("GI");
        value.goods = goods;
        value.url = url;
        value.isMain = (byte) (main ? 1 : 0);
        value.sortNo = sortNo;
        return value;
    }

    public boolean isMain() {
        return isMain == 1;
    }

    public void setMain(boolean main) {
        this.isMain = (byte) (main ? 1 : 0);
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

    public String getUrl() {
        return url;
    }

    public int getSortNo() {
        return sortNo;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }
}
