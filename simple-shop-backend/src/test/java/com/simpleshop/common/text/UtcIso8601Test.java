package com.simpleshop.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 时间输出格式的回归测试。
 *
 * <p>重点钉住三件事：输出形状、截断到秒、与 JVM 默认时区无关。
 */
class UtcIso8601Test {

    private static final String CONTRACT_SHAPE = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z";

    private TimeZone originalDefaultZone;

    @BeforeEach
    void rememberDefaultZone() {
        originalDefaultZone = TimeZone.getDefault();
    }

    @AfterEach
    void restoreDefaultZone() {
        TimeZone.setDefault(originalDefaultZone);
    }

    @Test
    @DisplayName("of(LocalDateTime)：精确输出 2026-10-05T12:34:56Z")
    void formatsLocalDateTimeExactly() {
        assertThat(UtcIso8601.of(LocalDateTime.of(2026, 10, 5, 12, 34, 56)))
                .isEqualTo("2026-10-05T12:34:56Z");
    }

    @Test
    @DisplayName("ofInstant：精确输出 2026-10-05T12:34:56Z")
    void formatsInstantExactly() {
        assertThat(UtcIso8601.ofInstant(Instant.parse("2026-10-05T12:34:56Z")))
                .isEqualTo("2026-10-05T12:34:56Z");
    }

    @ParameterizedTest(name = "LocalDateTime 形状：{0}")
    @ValueSource(strings = {
            "2026-01-01T00:00:00",
            "2026-12-31T23:59:59",
            "2024-02-29T12:00:00"})
    @DisplayName("of(LocalDateTime)：任意合法时刻都匹配契约形状")
    void localDateTimeAlwaysMatchesContractShape(String text) {
        assertThat(UtcIso8601.of(LocalDateTime.parse(text))).matches(CONTRACT_SHAPE);
    }

    @Test
    @DisplayName("截断到秒：库值与带纳秒的内存值必须给出相同字符串")
    void truncatesSubSecondPrecisionSoDbAndMemoryAgree() {
        LocalDateTime fromDatabase = LocalDateTime.of(2026, 10, 5, 12, 34, 56, 0);
        LocalDateTime inMemory = LocalDateTime.of(2026, 10, 5, 12, 34, 56, 123_456_789);

        assertThat(UtcIso8601.of(inMemory))
                .isEqualTo("2026-10-05T12:34:56Z")
                .isEqualTo(UtcIso8601.of(fromDatabase));
    }

    @Test
    @DisplayName("截断到秒：Instant 的纳秒同样被截断")
    void truncatesInstantSubSecondPrecision() {
        assertThat(UtcIso8601.ofInstant(Instant.parse("2026-10-05T12:34:56.987654321Z")))
                .isEqualTo("2026-10-05T12:34:56Z");
    }

    @Test
    @DisplayName("空值返回 null，而不是空串")
    void nullMapsToNullNotEmptyString() {
        assertThat(UtcIso8601.of(null)).isNull();
        assertThat(UtcIso8601.ofInstant(null)).isNull();
    }

    @Test
    @DisplayName("输出与 JVM 默认时区无关")
    void outputIsIndependentOfJvmDefaultTimeZone() {
        LocalDateTime utcWallClock = LocalDateTime.of(2026, 10, 5, 12, 34, 56);
        Instant sameMoment = Instant.parse("2026-10-05T12:34:56Z");

        assertThat(UtcIso8601.of(utcWallClock)).isEqualTo("2026-10-05T12:34:56Z");

        for (String zoneId : List.of("Asia/Shanghai", "America/New_York", "Pacific/Kiritimati")) {
            TimeZone.setDefault(TimeZone.getTimeZone(zoneId));

            assertThat(UtcIso8601.of(utcWallClock))
                    .as("默认时区改为 %s 后 LocalDateTime 输出必须不变", zoneId)
                    .isEqualTo("2026-10-05T12:34:56Z");

            assertThat(UtcIso8601.ofInstant(sameMoment))
                    .as("默认时区改为 %s 后 Instant 输出必须不变", zoneId)
                    .isEqualTo("2026-10-05T12:34:56Z");
        }
    }
}
