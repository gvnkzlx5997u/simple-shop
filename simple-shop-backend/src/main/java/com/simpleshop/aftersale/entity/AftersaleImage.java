package com.simpleshop.aftersale.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.simpleshop.common.id.Ids;
import com.simpleshop.common.text.TextRules;

/**
 * 售后图片。只记 URL 与展示顺序，不复制文件。
 *
 * <p>URL 必须指向本服务为该用途落盘的文件，回验在服务层用媒体服务完成；
 * 这里只管非空、长度与顺序。数量上限（9 张）是申请单的整体规则，由服务层校验。
 */
@Entity
@Table(name = "simpleshop_aftersale_image")
public class AftersaleImage {

    private static final int URL_MAX = 255;

    @Id
    @Column(length = 50)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_aftersale_id", nullable = false)
    private Aftersale aftersale;

    @Column(nullable = false, length = URL_MAX)
    private String url;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;

    protected AftersaleImage() {
    }

    public static AftersaleImage add(Aftersale aftersale, String url, int sortNo) {
        if (aftersale == null) {
            throw new IllegalArgumentException("aftersale is required");
        }
        if (url == null || url.isBlank() || TextRules.charCount(url) > URL_MAX) {
            throw new IllegalArgumentException("image url is invalid");
        }
        if (sortNo < 1) {
            throw new IllegalArgumentException("sort no must be at least 1");
        }

        AftersaleImage image = new AftersaleImage();
        image.id = Ids.next("AI");
        image.aftersale = aftersale;
        image.url = url.trim();
        image.sortNo = sortNo;
        return image;
    }

    public String getId() {
        return id;
    }

    public Aftersale getAftersale() {
        return aftersale;
    }

    public String getUrl() {
        return url;
    }

    public int getSortNo() {
        return sortNo;
    }
}
