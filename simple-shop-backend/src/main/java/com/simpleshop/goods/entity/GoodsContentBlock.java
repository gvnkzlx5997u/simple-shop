package com.simpleshop.goods.entity;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.simpleshop.common.id.Ids;
import com.simpleshop.common.time.DatabaseTimeProvider;
import com.simpleshop.goods.enums.ContentBlockType;

/**
 * 商品描述内容块：纯文本、图片或视频，按 sort_no 混排。
 */
@Entity
@Table(name = "simpleshop_goods_content_block",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_goods_content_block_sort", columnNames = {"fk_goods_id", "sort_no"}))
public class GoodsContentBlock {

    @Id
    @Column(length = 50)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_goods_id", nullable = false)
    private Goods goods;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false, length = 10)
    private ContentBlockType blockType;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;

    @Column(name = "text_content", length = 1000)
    private String textContent;

    @Column(name = "media_url", length = 512)
    private String mediaUrl;

    @Column(name = "create_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime createAt;

    protected GoodsContentBlock() {
    }

    public static GoodsContentBlock text(Goods goods, String text, int sortNo) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text block must not be blank");
        }
        if (text.codePointCount(0, text.length()) > 1000) {
            throw new IllegalArgumentException("single text block must not exceed 1000 characters");
        }
        GoodsContentBlock value = base(goods, ContentBlockType.TEXT, sortNo);
        value.textContent = text;
        return value;
    }

    public static GoodsContentBlock image(Goods goods, String url, int sortNo) {
        return media(goods, ContentBlockType.IMAGE, url, sortNo);
    }

    public static GoodsContentBlock video(Goods goods, String url, int sortNo) {
        return media(goods, ContentBlockType.VIDEO, url, sortNo);
    }

    private static GoodsContentBlock media(
            Goods goods, ContentBlockType type, String url, int sortNo) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("media block must have a url");
        }
        GoodsContentBlock value = base(goods, type, sortNo);
        value.mediaUrl = url;
        return value;
    }

    private static GoodsContentBlock base(Goods goods, ContentBlockType type, int sortNo) {
        if (sortNo < 1) {
            throw new IllegalArgumentException("sort_no must be at least 1");
        }
        GoodsContentBlock value = new GoodsContentBlock();
        value.id = Ids.next("GB");
        value.goods = goods;
        value.blockType = type;
        value.sortNo = sortNo;
        return value;
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

    public ContentBlockType getBlockType() {
        return blockType;
    }

    public int getSortNo() {
        return sortNo;
    }

    public String getTextContent() {
        return textContent;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }
}
