package com.simpleshop.order.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simpleshop.order.repository.OrderRepository;

/**
 * 订单号格式与序列：不起数据库，仓储用替身返回「当日已用最大序列」。
 */
class OrderNoGeneratorTest {

    private final OrderRepository orders = mock(OrderRepository.class);

    @Test
    @DisplayName("订单号是 SO + UTC 日期 + 6 位序列，从库内当日最大序列继续")
    void formatsPrefixDayAndSequence() {
        when(orders.maxSequenceOfDay("SO20261109")).thenReturn(41L);
        OrderNoGenerator generator = generatorAt("2026-11-09T10:00:00Z");

        assertThat(generator.next()).isEqualTo("SO20261109000042");
        assertThat(generator.next()).isEqualTo("SO20261109000043");
        verify(orders, times(1)).maxSequenceOfDay("SO20261109");
    }

    @Test
    @DisplayName("日期取 UTC 而不是机器所在时区：UTC 20 点仍算当天，不跳成次日")
    void usesUtcDayRatherThanLocalZone() {
        when(orders.maxSequenceOfDay("SO20261109")).thenReturn(0L);
        // 东八区已经跨到 11-10，UTC 还是 11-09；订单号按库内的 UTC 口径走。
        OrderNoGenerator generator = new OrderNoGenerator(
                orders, Clock.fixed(Instant.parse("2026-11-09T20:00:00Z"), ZoneOffset.ofHours(8)));

        assertThat(generator.next()).isEqualTo("SO20261109000001");
    }

    @Test
    @DisplayName("跨天自动换前缀并重新从库内取起点")
    void reseedsWhenDayChanges() {
        Clock clock = new MutableClock(Instant.parse("2026-11-09T08:00:00Z"));
        when(orders.maxSequenceOfDay(anyString()))
                .thenAnswer(call -> call.getArgument(0, String.class).endsWith("20261109") ? 5L : 100L);
        OrderNoGenerator generator = new OrderNoGenerator(orders, clock);

        assertThat(generator.next()).isEqualTo("SO20261109000006");
        assertThat(generator.next()).isEqualTo("SO20261109000007");

        ((MutableClock) clock).set(Instant.parse("2026-11-17T08:00:00Z"));

        assertThat(generator.next()).isEqualTo("SO20261117000101");
        assertThat(generator.next()).isEqualTo("SO20261117000102");
        verify(orders).maxSequenceOfDay("SO20261109");
        verify(orders).maxSequenceOfDay("SO20261117");
    }

    @Test
    @DisplayName("并发取号不重复、不跳号")
    void allocatesUniqueNumbersUnderConcurrency() throws Exception {
        when(orders.maxSequenceOfDay(anyString())).thenReturn(0L);
        OrderNoGenerator generator = generatorAt("2026-11-09T10:00:00Z");

        int threads = 8;
        int perThread = 200;
        Set<String> numbers = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        for (int j = 0; j < perThread; j++) {
                            numbers.add(generator.next());
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(numbers).hasSize(threads * perThread);
        assertThat(numbers).allMatch(number -> number.matches("SO20261109\\d{6}"));
        assertThat(numbers).contains("SO20261109000001", "SO20261109001600");
    }

    @Test
    @DisplayName("库内序列已用满 6 位时继续加长，不回绕到 1")
    void keepsGrowingBeyondSixDigits() {
        when(orders.maxSequenceOfDay("SO20261109")).thenReturn(999_999L);
        OrderNoGenerator generator = generatorAt("2026-11-09T10:00:00Z");

        assertThat(generator.next()).isEqualTo("SO202611091000000");
    }

    private OrderNoGenerator generatorAt(String instant) {
        return new OrderNoGenerator(orders, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    /** 可推进的时钟：验证同一天与跨天两种取号路径。 */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void set(Instant value) {
            this.instant = value;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
