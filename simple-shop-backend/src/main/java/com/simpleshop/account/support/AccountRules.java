package com.simpleshop.account.support;

import java.nio.charset.StandardCharsets;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.text.TextRules;

/**
 * 账号字段的业务校验。
 *
 * <p>用户名/账号：去首尾空白后 1~50 字符，不允许空白、控制字符、换行和尖括号。
 * 密码：注册/改密至少 8 字符，且 UTF-8 不超过 72 字节（BCrypt 的有效输入上限）。
 * 电话：7~20 位数字与 "-"，格式在这里校验，数据库不限制。
 * 默认交易地点：可空，最多 50 字符，不允许换行和尖括号。
 */
public final class AccountRules {

    private AccountRules() {
    }

    public static String username(String raw) {
        return loginName(raw, ErrorCode.USERNAME_INVALID);
    }

    public static String account(String raw) {
        return loginName(raw, ErrorCode.ACCOUNT_INVALID);
    }

    public static String newPassword(String raw) {
        if (raw == null
                || raw.isBlank()
                || TextRules.charCount(raw) < 8
                || raw.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.PASSWORD_INVALID);
        }
        return raw;
    }

    public static String loginPassword(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.PASSWORD_INVALID);
        }
        return raw;
    }

    public static String phone(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.matches("[0-9-]{7,20}")) {
            throw new BusinessException(ErrorCode.PHONE_INVALID);
        }
        return value;
    }

    public static String defaultLocation(String raw) {
        String value = TextRules.trimToNull(raw);
        if (value != null
                && (TextRules.charCount(value) > 50
                || TextRules.containsAngleBracket(value)
                || TextRules.containsLineBreak(value))) {
            throw new BusinessException(ErrorCode.DEFAULT_LOCATION_INVALID);
        }
        return value;
    }

    private static String loginName(String raw, ErrorCode errorCode) {
        if (raw == null) {
            throw new BusinessException(errorCode);
        }
        String value = raw.trim();
        if (value.isEmpty()
                || TextRules.charCount(value) > 50
                || TextRules.containsAngleBracket(value)
                || TextRules.containsLineBreak(value)
                || value.codePoints().anyMatch(cp -> Character.isWhitespace(cp) || Character.isISOControl(cp))) {
            throw new BusinessException(errorCode);
        }
        return value;
    }
}
