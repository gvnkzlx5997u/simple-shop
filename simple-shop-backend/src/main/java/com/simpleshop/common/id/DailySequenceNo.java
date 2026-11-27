package com.simpleshop.common.id;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 「前缀 + UTC 日期 + 每日序列」编号生成，订单号与售后号共用。
 *
 * <p>为什么日期只到天、不带小时：库内与接口统一 UTC，编号却是给人念的，写进小时会让人
 * 按本地时间去读，产生时区歧义。精确时间在 {@code create_at} 里。
 *
 * <p>序列来源：进程内按天递增，某天首次取号时从库里读当日最大序列作为起点，重启不会从头开始。
 * 项目单副本部署（会话在进程内），同一时刻只有一个进程取号；唯一索引是兜底断言，
 * 真撞上说明取号逻辑出错，按服务端错误暴露。生成器不做隐式重试：撞号发生在插入阶段，
 * 事务已被标记回滚，同事务内换号没有意义。
 *
 * <p>跨天时整体替换计数器：旧计数器上仍在取号的线程拿到的仍是旧日期前缀，不会与新一天重号。
 */
public class DailySequenceNo {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final String prefix;
    private final int digits;
    private final Function<String, Long> maxOfDay;
    private final Clock clock;
    private final AtomicReference<DailySequence> current = new AtomicReference<>();

    /**
     * @param maxOfDay 给定「前缀 + 日期」，返回库里当日已用的最大序列；没有则 0
     */
    public DailySequenceNo(String prefix, int digits, Function<String, Long> maxOfDay, Clock clock) {
        this.prefix = prefix;
        this.digits = digits;
        this.maxOfDay = maxOfDay;
        this.clock = clock;
    }

    public String next() {
        String dayPrefix = prefix + LocalDate.now(clock.withZone(ZoneOffset.UTC))
                .format(DAY_FORMAT);
        DailySequence sequence = current.updateAndGet(existing ->
                existing != null && existing.prefix.equals(dayPrefix)
                        ? existing
                        : new DailySequence(dayPrefix, maxOfDay.apply(dayPrefix)));
        long value = sequence.value.incrementAndGet();
        return dayPrefix + String.format(Locale.ROOT, "%0" + digits + "d", value);
    }

    private static final class DailySequence {

        private final String prefix;
        private final AtomicLong value;

        private DailySequence(String prefix, long seed) {
            this.prefix = prefix;
            this.value = new AtomicLong(seed);
        }
    }
}
