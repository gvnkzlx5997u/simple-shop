package com.simpleshop.common.text;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 时间输出的唯一转换点：UTC 时刻转 ISO-8601 字符串。
 *
 * <p>输出精确到秒并带 {@code Z}；不得使用系统默认时区。
 */
public final class UtcIso8601 {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private UtcIso8601() {
    }

    public static String of(LocalDateTime utc) {
        if (utc == null) {
            return null;
        }
        return FORMATTER.format(utc.truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC));
    }

    public static String ofInstant(Instant instant) {
        if (instant == null) {
            return null;
        }
        return FORMATTER.format(instant.truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC));
    }
}
