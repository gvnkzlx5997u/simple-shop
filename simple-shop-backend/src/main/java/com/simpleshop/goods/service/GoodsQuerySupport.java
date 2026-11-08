package com.simpleshop.goods.service;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.simpleshop.category.dto.CategorySelection;
import com.simpleshop.category.service.CategoryService;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.api.PageQuery;
import com.simpleshop.common.config.AppProperties;
import com.simpleshop.common.text.TextRules;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.enums.OffShelfReason;

/**
 * 商品查询参数的统一解析：分页、状态、关键字与类别层级。
 */
@Component
public class GoodsQuerySupport {

    private static final int MAX_KEYWORD_LENGTH = 50;

    private final CategoryService categories;
    private final AppProperties properties;

    public GoodsQuerySupport(CategoryService categories, AppProperties properties) {
        this.categories = categories;
        this.properties = properties;
    }

    public PageQuery page(Integer page, Integer pageSize) {
        return PageQuery.of(page, pageSize, properties);
    }

    public GoodsStatus status(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return GoodsStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
    }

    public String keyword(String raw) {
        String value = TextRules.trimToNull(raw);
        if (value != null && TextRules.charCount(value) > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        return value;
    }

    /**
     * 下架原因只能与已下架组合：带原因却没筛 OFF_SHELF 属于自相矛盾的查询，
     * 直接按参数错误拒绝，而不是静默返回空列表。
     */
    public OffShelfReason offShelfReason(GoodsStatus status, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (status != GoodsStatus.OFF_SHELF) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        try {
            return OffShelfReason.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
    }

    /**
     * category_id 可以是一级或二级：一级匹配其下全部二级商品。
     */
    public CategoryFilter category(String raw) {
        CategorySelection selection = categories.resolveSelection(raw);
        return new CategoryFilter(selection.id(), selection.level());
    }

    public record CategoryFilter(String id, int level) {
    }
}
