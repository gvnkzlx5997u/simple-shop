package com.simpleshop.order.support;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.simpleshop.common.id.DailySequenceNo;
import com.simpleshop.order.repository.OrderRepository;

/**
 * 订单号生成：{@code SO} + {@code yyyyMMdd} + 6 位每日序列，例如 {@code SO20261109000042}。
 *
 * <p>格式、取号与并发口径都在 {@link DailySequenceNo} 里，售后号共用同一套实现——
 * 两处各写一份补零逻辑，迟早出现一个补 4 位、一个补 6 位。
 *
 * <p>订单号唯一但不是主键：主键是 {@code O + UUID}，用 UUID 是为了让主键不携带业务含义。
 */
@Component
public class OrderNoGenerator {

    static final String PREFIX = "SO";

    /** 6 位可容纳单日 99 万单，序列超出宽度时继续加长，不截断、不回绕。 */
    static final int SEQUENCE_DIGITS = 6;

    private final DailySequenceNo numbers;

    @Autowired
    public OrderNoGenerator(OrderRepository orders) {
        this(orders, Clock.systemUTC());
    }

    /** 供测试固定时钟；生产只走 UTC 时钟，不读 JVM 默认时区。 */
    OrderNoGenerator(OrderRepository orders, Clock clock) {
        this.numbers = new DailySequenceNo(
                PREFIX, SEQUENCE_DIGITS, orders::maxSequenceOfDay, clock);
    }

    public String next() {
        return numbers.next();
    }
}
