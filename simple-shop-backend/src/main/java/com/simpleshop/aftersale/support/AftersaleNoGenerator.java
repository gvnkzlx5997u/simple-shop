package com.simpleshop.aftersale.support;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.simpleshop.aftersale.repository.AftersaleRepository;
import com.simpleshop.common.id.DailySequenceNo;

/**
 * 售后单号：{@code AS} + {@code yyyyMMdd} + 4 位每日序列，例如 {@code AS202611260001}。
 *
 * <p>与订单号同一套实现（{@link DailySequenceNo}），只是前缀不同、位数少两位：
 * 单日 9999 张售后单对单卖家系统足够，超出宽度会继续加长，不截断。
 */
@Component
public class AftersaleNoGenerator {

    static final String PREFIX = "AS";

    static final int SEQUENCE_DIGITS = 4;

    private final DailySequenceNo numbers;

    @Autowired
    public AftersaleNoGenerator(AftersaleRepository aftersales) {
        this(aftersales, Clock.systemUTC());
    }

    AftersaleNoGenerator(AftersaleRepository aftersales, Clock clock) {
        this.numbers = new DailySequenceNo(
                PREFIX, SEQUENCE_DIGITS, aftersales::maxSequenceOfDay, clock);
    }

    public String next() {
        return numbers.next();
    }
}
