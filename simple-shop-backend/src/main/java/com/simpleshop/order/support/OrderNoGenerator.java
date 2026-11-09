package com.simpleshop.order.support;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.simpleshop.order.repository.OrderRepository;

/**
 * 订单号生成：{@code SO} + {@code yyyyMMdd} + 6 位每日序列，例如 {@code SO20261109000042}。
 *
 * <p>为什么不带小时：库内、接口与日志统一用 UTC，而订单号是给人看和念的；
 * 把小时写进去会让人按本地时间去读，产生时区歧义。日期只承担「哪一天下单」的粗粒度语义，
 * 精确时间本来就在 {@code create_at} 里。
 *
 * <p>订单号唯一但不是主键：主键是 {@code O + UUID}，用 UUID 是为了让主键不携带业务含义；
 * 订单号只是为了人类可读，格式将来可以变，不能承担主键职责。
 *
 * <p>序列来源：进程内按天递增，某天首次取号时从库里读当日最大序列作为起点，
 * 因此重启不会从头开始。项目按单副本部署（会话在进程内，backend 不允许多副本），
 * 同一时刻只有一个进程取号，加上跨天与重启的续号，本设计下不会撞号；
 * {@code uk_order_no} 唯一索引是兜底断言，真撞上说明取号逻辑出错，按服务端错误暴露。
 * 生成器不做隐式重试：撞号发生在插入阶段，此时事务已被标记回滚，同事务内换号没有意义。
 */
@Component
public class OrderNoGenerator {

    static final String PREFIX = "SO";

    /** 6 位可容纳单日 99 万单，序列超出宽度时继续加长，不截断、不回绕。 */
    static final int SEQUENCE_DIGITS = 6;

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderRepository orders;
    private final Clock clock;
    private final AtomicReference<DailySequence> current = new AtomicReference<>();

    @Autowired
    public OrderNoGenerator(OrderRepository orders) {
        this(orders, Clock.systemUTC());
    }

    /** 供测试固定时钟；生产只走 UTC 时钟，不读 JVM 默认时区。 */
    OrderNoGenerator(OrderRepository orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    public String next() {
        String dayPrefix = PREFIX + LocalDate.now(clock.withZone(ZoneOffset.UTC)).format(DAY_FORMAT);
        DailySequence sequence = current.updateAndGet(existing ->
                existing != null && existing.prefix.equals(dayPrefix)
                        ? existing
                        : new DailySequence(dayPrefix, orders.maxSequenceOfDay(dayPrefix)));
        long value = sequence.value.incrementAndGet();
        return dayPrefix + String.format(Locale.ROOT, "%0" + SEQUENCE_DIGITS + "d", value);
    }

    /**
     * 一天的序列计数器。跨天时整体替换，旧计数器上仍在取号的线程拿到的前缀还是旧日期，
     * 与新年份的号不会重复。
     */
    private static final class DailySequence {

        private final String prefix;
        private final AtomicLong value;

        private DailySequence(String prefix, long seed) {
            this.prefix = prefix;
            this.value = new AtomicLong(seed);
        }
    }
}
