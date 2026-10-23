package com.simpleshop.common.text;

/**
 * 各字段共用的纯文本判定。长度按码点计算，与数据库的字符数口径一致。
 *
 * <p>统一规则：拒绝尖括号，避免把 HTML 片段当普通文本入库；输出侧仍必须转义，
 * 输入拒绝不能替代输出转义。
 */
public final class TextRules {

    private TextRules() {
    }

    public static int charCount(String value) {
        return value.codePointCount(0, value.length());
    }

    public static boolean containsAngleBracket(String value) {
        return value.indexOf('<') >= 0 || value.indexOf('>') >= 0;
    }

    public static boolean containsLineBreak(String value) {
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }

    /** 是否包含任意 ISO 控制字符（含换行、制表符）。 */
    public static boolean containsControlChar(String value) {
        return value != null && value.codePoints().anyMatch(Character::isISOControl);
    }

    /** 是否包含除换行、回车、制表符之外的控制字符；用于允许换行的描述文本。 */
    public static boolean containsDisallowedControlChar(String value) {
        return value != null && value.codePoints().anyMatch(codePoint ->
                Character.isISOControl(codePoint)
                        && codePoint != '\n' && codePoint != '\r' && codePoint != '\t');
    }

    /** 去掉首尾空白；结果为空串时返回 {@code null}。 */
    public static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
