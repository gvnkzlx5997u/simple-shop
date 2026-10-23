package com.simpleshop.category.service;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.text.TextRules;

/**
 * 类别名称规则：去首尾空白后 1~50 字符，拒绝控制字符、换行与尖括号。
 */
public final class CategoryRules {

    private static final int MAX_NAME_LENGTH = 50;

    private CategoryRules() {
    }

    public static String name(String raw) {
        String value = TextRules.trimToNull(raw);
        if (value == null
                || TextRules.charCount(value) > MAX_NAME_LENGTH
                || TextRules.containsControlChar(value)
                || TextRules.containsAngleBracket(value)) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_INVALID);
        }
        return value;
    }
}
