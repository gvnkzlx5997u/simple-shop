package com.simpleshop.service.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * DTO 时间字段的<b>唯一</b>转换点：{@code 库内 UTC 时刻 → ISO 8601 带 Z 的字符串}。
 *
 * <p>依据：第 11 章 §11.3.1（时间格式）、{@code C-10}／{@code C-11}、{@code NFR-18}、
 * 方案 §4.6.4、以及已定 Q-11（<b>服务端只下发 UTC，UTC+8 的展示转换由前端做</b>）。
 *
 * <h2>为什么单独一个类，而不是「每个 DTO 各写一遍」</h2>
 * <p>方案 §4.6.4 要求「时间 → {@code String} 的转换点写在 DTO 的静态工厂里」。这一点被保留——
 * DTO 静态工厂<b>仍然</b>是调用点。但把格式化<b>实现</b>收敛到这一处，有两个实在的理由：
 * <ol>
 *   <li><b>「禁止系统时区」这条硬规则只需要在一个地方被审。</b>方案 §4.6.4 明确：
 *       「<b>绝对禁止</b>在转换里出现 {@code ZoneId.systemDefault()}／{@code ZoneOffset.systemDefault()}」——
 *       开发机是 {@code Asia/Shanghai}，误用会<b>静默偏 8 小时</b>，正是数据层 §4.1 那个缺陷的同类。
 *       若 10 个 DTO 各写一份 {@code iso(...)}，就有 10 处需要逐个核对；
 *       收在一处后，只需看这一个方法。</li>
 *   <li><b>截断到秒这件事必须统一。</b>见下节——漏掉它的 DTO 会单独违反契约，
 *       而这是最容易漏的一步（漏了编译不报错、运行不报错，只有对着契约看才发现）。</li>
 * </ol>
 *
 * <h2>⚠️ 必须截断到秒（本类最容易漏的一步）</h2>
 * <p>契约与 §8.3 断言 #3 要求时间格式<b>恰好</b>是 {@code yyyy-MM-ddTHH:mm:ssZ}。
 * 而两个来源的精度不同：
 * <ul>
 *   <li><b>库列</b>：{@code V1__baseline_schema.sql} 全部时间列都是 {@code datetime}
 *       （<b>无小数秒</b>），从库里读回来的值小数秒恒为 0；</li>
 *   <li><b>进程内时刻</b>：{@code DatabaseTimeProvider.utcNow()} 内部是
 *       {@code LocalDateTime.now(ZoneOffset.UTC)}，{@link Instant#now()} 同理——
 *       <b>都带纳秒／微秒</b>。若直接格式化，会输出 {@code 2026-10-05T12:34:56.123456Z}，
 *       与契约形状不符。</li>
 * </ul>
 * <p>关键在于：<b>同一个 DTO 字段可能两种来源都走</b>——实体刚 {@code persist} 时字段里是
 * 带小数秒的内存值，重新从库里读出来的是不带小数秒的值。所以这不是「某个接口碰巧有问题」，
 * 而是「不截断就一定会在某条路径上违反契约」。因此本类<b>无条件</b>截断到秒。
 *
 * <h2>⚠️ 与 {@code WRITE_DATES_AS_TIMESTAMPS} 等的区别</h2>
 * <p>本类<b>不</b>依赖任何全局 Jackson 配置（{@code JacksonConfig} 里刻意没有注册
 * {@code LocalDateTime}／{@code BigDecimal} 的全局序列化器，见该类的说明）。
 * 全局配置的问题在于「将来有人改了它，格式会静默漂移」；这里格式完全受控。
 *
 * <h2>⚠️ 为什么不用 {@code Instant} 直接配 {@code ISO_OFFSET_DATE_TIME}</h2>
 * <p>{@link Instant} <b>没有</b> {@code OFFSET_SECONDS}、{@code EPOCH_DAY} 等字段，
 * 直接用它格式化 {@code ISO_OFFSET_DATE_TIME} 会抛
 * {@code UnsupportedTemporalTypeException}。必须先 {@code atOffset(ZoneOffset.UTC)} 补上时区上下文。
 */
public final class UtcIso8601 {

    /**
     * 契约要求的输出形状（{@code 2026-10-05T12:34:56Z}）。
     *
     * <p>用标准的 {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME} 而不是自定义 pattern：
     * 它在偏移为 0 时输出 {@code Z}（内部即 {@code appendOffset("+HH:MM:ss", "Z")}），
     * 且在纳秒为 0 时<b>省略</b>小数部分——两者合起来恰好是契约要求的形状，
     * 无需自己拼 pattern（自己拼反而容易写错分隔符或缺 {@code Z}）。
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private UtcIso8601() {
    }

    /**
     * 把「库内 UTC 墙上时间」转成契约要求的 ISO 8601 字符串。
     *
     * <p>用于所有从 {@code datetime} 列读出的时间（{@code create_at}、{@code trade_end}、
     * {@code trade_start} 等）。
     *
     * @param utc 库内 UTC 时刻（{@code null} 表示该列本身可空且无值）
     * @return 形如 {@code 2026-10-05T12:34:56Z}；入参为 {@code null} 时返回 {@code null}
     *         （契约 §11.3.1：可选字段无值时下发 {@code null}，<b>但键必须存在</b>——
     *         返回 {@code null} 而非空串，正是为了让键保留下来）
     */
    public static String of(LocalDateTime utc) {
        if (utc == null) {
            return null;
        }
        // ⚠️ 这里【必须】用显式的 ZoneOffset.UTC：LocalDateTime 本身不带时区，
        //    若不显式指定，格式化会取 JVM 默认时区（本机 UTC+8），静默偏 8 小时。
        return FORMATTER.format(utc.truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC));
    }

    /**
     * 把「进程内时刻」转成契约要求的 ISO 8601 字符串。
     *
     * <p>用于并非来自数据库的时刻，目前唯一用例是 {@code I11-01} 的 {@code expire_at}
     * （会话过期时刻，由 {@code SessionStore} 用 {@link Instant} 计算）。
     *
     * <p><b>⚠️ 与 {@link #of(LocalDateTime)} 的关系</b>：二者输出的字符串形状<b>完全一致</b>，
     * 差别只在入参类型。刻意<b>不</b>做成同名重载——同名重载会让 {@code of(null)}
     * 这种调用产生歧义（编译期直接报错），而 {@code null} 恰恰是本类要正常处理的输入。
     *
     * @param instant 时刻（{@code null} 时返回 {@code null}）
     * @return 形如 {@code 2026-10-05T13:04:56Z}
     */
    public static String ofInstant(Instant instant) {
        if (instant == null) {
            return null;
        }
        return FORMATTER.format(instant.truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC));
    }
}
