package com.simpleshop.service.support;

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
 * {@link UtcIso8601} 的行为验证——<b>时间格式契约的回归护栏</b>。
 *
 * <p>对应方案 §8.3「JSON 契约」断言 #3：「<b>时间格式恰为 {@code yyyy-MM-ddTHH:mm:ssZ}（含末尾 {@code Z}）</b>」，
 * 以及 §4.6.4 的两条硬规则：① 必须带 {@code Z}；② <b>绝对禁止</b> {@code systemDefault()}。
 *
 * <h2>为什么给一个「只有两行的方法」写这么多用例</h2>
 * <p>因为这类错误的特征是<b>不会报错</b>：
 * <ul>
 *   <li>漏了 {@code Z} → JSON 里是 {@code 2026-10-05T12:34:56}，前端 {@code new Date(...)} 会
 *       按<b>浏览器本地时区</b>解释，东八区用户看到的时间偏 8 小时，而服务端日志一切正常；</li>
 *   <li>误用系统时区 → 输出 {@code 2026-10-05T20:34:56Z}（差了 8 小时却<b>仍带 Z</b>），
 *       连「是否带 Z」的检查都拦不住它，只有把默认时区换掉才能发现。</li>
 * </ul>
 * <p>所以下面用「改 JVM 默认时区」的方式把第二条钉住——这正是数据层 §4.1 那个缺陷的同类，
 * 当时的教训是「{@code validate} 查不出来」。
 *
 * <h2>本类<b>不</b>启动 Spring 上下文</h2>
 * <p>它是纯函数，不碰数据库，也就不该付一次容器启动的代价。
 */
class UtcIso8601Test {

    /** 契约要求的精确形状（方案 §8.3 断言 #3）。 */
    private static final String CONTRACT_SHAPE = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z";

    private TimeZone originalDefaultZone;

    @BeforeEach
    void rememberDefaultZone() {
        originalDefaultZone = TimeZone.getDefault();
    }

    @AfterEach
    void restoreDefaultZone() {
        // 改默认时区是全局副作用，必须还原，否则会污染同 JVM 内的其它用例
        // （Hibernate 的 jdbc.time_zone 虽已显式设为 UTC，但 JDBC 驱动报错信息、
        //   日志时间戳等仍会受默认时区影响）。
        TimeZone.setDefault(originalDefaultZone);
    }

    // -------------------------------------------------------------------------
    // 形状
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // 小数秒截断（DB 列与内存值的精度不一致 —— 最容易漏的一步）
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("截断到秒：库值（无小数秒）与内存值（带纳秒）必须给出【相同】的字符串")
    void truncatesSubSecondPrecisionSoDbAndMemoryAgree() {
        // 库列是 datetime（V1 建表，无小数秒），而 DatabaseTimeProvider.utcNow() 内部是
        // LocalDateTime.now(ZoneOffset.UTC)，【带纳秒】。同一个 DTO 字段两种来源都会走到，
        // 不截断就必然在「刚写入、未重读」的路径上输出 ...T12:34:56.123456789Z。
        LocalDateTime fromDatabase = LocalDateTime.of(2026, 10, 5, 12, 34, 56, 0);
        LocalDateTime inMemory = LocalDateTime.of(2026, 10, 5, 12, 34, 56, 123_456_789);

        assertThat(UtcIso8601.of(inMemory))
                .as("带纳秒的内存值必须被截断到秒")
                .isEqualTo("2026-10-05T12:34:56Z")
                .isEqualTo(UtcIso8601.of(fromDatabase));
    }

    @Test
    @DisplayName("截断到秒：Instant 的纳秒同样被截断（I11-01 的 expire_at 走这条）")
    void truncatesInstantSubSecondPrecision() {
        assertThat(UtcIso8601.ofInstant(Instant.parse("2026-10-05T12:34:56.987654321Z")))
                .isEqualTo("2026-10-05T12:34:56Z");
    }

    // -------------------------------------------------------------------------
    // 空值：必须返回 null 而不是空串（契约要求键存在）
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("空值 → null（不是空串）：契约要求「无值时下发 null，但键必须存在」")
    void nullMapsToNullNotEmptyString() {
        // 返回 "" 会让前端拿到一个「看起来有值但无法解析」的时间；
        // 返回 null 才是契约口径（11.3.1）。而键是否存在由 DTO 字段本身保证——
        // 前提是【没有】配置 @JsonInclude(NON_NULL)（见 ApiResponse 的类注释与 AppPropertiesTest）。
        assertThat(UtcIso8601.of(null)).isNull();
        assertThat(UtcIso8601.ofInstant(null)).isNull();
    }

    // -------------------------------------------------------------------------
    // 默认时区无关性（本节是本测试类存在的核心理由）
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("输出与 JVM 默认时区【无关】—— 误用 systemDefault 的回归护栏")
    void outputIsIndependentOfJvmDefaultTimeZone() {
        LocalDateTime utcWallClock = LocalDateTime.of(2026, 10, 5, 12, 34, 56);
        Instant sameMoment = Instant.parse("2026-10-05T12:34:56Z");

        // 先固定一个基线（并且它必须已经带 Z）
        assertThat(UtcIso8601.of(utcWallClock)).isEqualTo("2026-10-05T12:34:56Z");

        // 覆盖「本机所在的东八区」、一个负偏移、以及一个极大正偏移（UTC+14），
        // 把「偷懒用了默认时区」的各种表现都逼出来：
        //   · 若实现对 LocalDateTime 用了 systemDefault() → 东八区下会输出 20:34:56（偏 8 小时）
        //   · 若实现对 Instant 用了 systemDefault()     → 会输出 ±偏移后的时间
        for (String zoneId : List.of("Asia/Shanghai", "America/New_York", "Pacific/Kiritimati")) {
            TimeZone.setDefault(TimeZone.getTimeZone(zoneId));

            assertThat(UtcIso8601.of(utcWallClock))
                    .as("默认时区改为 %s 后，LocalDateTime 的输出必须不变"
                            + "（变化即说明误用了 systemDefault，会静默偏 8 小时且【仍带 Z】，"
                            + "连格式断言都拦不住）", zoneId)
                    .isEqualTo("2026-10-05T12:34:56Z");

            assertThat(UtcIso8601.ofInstant(sameMoment))
                    .as("默认时区改为 %s 后，Instant 的输出必须不变", zoneId)
                    .isEqualTo("2026-10-05T12:34:56Z");
        }
    }
}
