package com.simpleshop.address.support;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.text.TextRules;

/**
 * 地址字段的业务校验。
 *
 * <p>三个字段都是「一行文本」：不做省市区拆分，也不允许换行——地址簿、订单快照、
 * 物流面单展示的是同一段文字，换行只会在其中一处错位。
 *
 * <p>电话沿用账号手机号的格式（7~20 位数字与 "-"），收货人同样允许中文与空格，
 * 只拒绝控制字符和尖括号（避免把 HTML 片段当普通文本入库）。
 */
public final class AddressRules {

    private static final int NAME_MAX = 50;
    private static final int PHONE_MAX = 20;
    private static final int DETAIL_MAX = 200;

    private AddressRules() {
    }

    public static String receiverName(String raw) {
        return singleLineText(raw, NAME_MAX, "receiver name");
    }

    public static String receiverPhone(String raw) {
        String value = singleLineText(raw, PHONE_MAX, "receiver phone");
        if (!value.matches("[0-9-]{7,20}") || value.chars().filter(Character::isDigit).count() < 7) {
            throw new BusinessException(ErrorCode.ADDRESS_FIELD_INVALID);
        }
        return value;
    }

    public static String detailAddress(String raw) {
        return singleLineText(raw, DETAIL_MAX, "detail address");
    }

    private static String singleLineText(String raw, int maxLength, String field) {
        String value = raw == null ? null : raw.trim();
        if (value == null
                || value.isEmpty()
                || TextRules.charCount(value) > maxLength
                || TextRules.containsLineBreak(value)
                || TextRules.containsControlChar(value)
                || TextRules.containsAngleBracket(value)) {
            throw new BusinessException(
                    ErrorCode.ADDRESS_FIELD_INVALID, field + " is invalid");
        }
        return value;
    }
}
