package com.simpleshop.goods.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.text.TextRules;

/**
 * 商品字段规则：名称、价格、库存与描述文本。
 */
public final class GoodsRules {

    public static final int MAX_NAME_LENGTH = 50;
    public static final int MAX_DESCRIPTION_TEXT_LENGTH = 1000;
    public static final BigDecimal MAX_PRICE = new BigDecimal("999999.99");

    private GoodsRules() {
    }

    public static String name(String raw) {
        String value = TextRules.trimToNull(raw);
        if (value == null
                || TextRules.charCount(value) > MAX_NAME_LENGTH
                || TextRules.containsControlChar(value)
                || TextRules.containsAngleBracket(value)) {
            throw new BusinessException(ErrorCode.GOODS_NAME_INVALID);
        }
        return value;
    }

    public static BigDecimal price(BigDecimal raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.PRICE_INVALID);
        }
        BigDecimal value = raw.stripTrailingZeros();
        if (value.compareTo(BigDecimal.ZERO) <= 0
                || value.compareTo(MAX_PRICE) > 0
                || value.scale() > 2) {
            throw new BusinessException(ErrorCode.PRICE_INVALID);
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static int publishStock(Integer raw) {
        if (raw == null || raw < 1) {
            throw new BusinessException(ErrorCode.STOCK_INVALID);
        }
        return raw;
    }

    public static int adjustStock(Integer raw) {
        if (raw == null || raw < 0) {
            throw new BusinessException(ErrorCode.STOCK_INVALID);
        }
        return raw;
    }

    public static String categoryId(String raw) {
        String value = TextRules.trimToNull(raw);
        if (value == null) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return value;
    }

    public static String descriptionText(String raw) {
        if (raw == null
                || raw.isBlank()
                || TextRules.charCount(raw) > MAX_DESCRIPTION_TEXT_LENGTH
                || TextRules.containsDisallowedControlChar(raw)
                || TextRules.containsAngleBracket(raw)) {
            throw new BusinessException(ErrorCode.GOODS_DESCRIPTION_INVALID);
        }
        return raw;
    }
}
