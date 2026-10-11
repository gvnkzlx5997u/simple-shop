package com.simpleshop.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 提交限流计数器的行为验证（{@code NFR-17}、{@code 12-P6}）。
 *
 * <p>本类<b>不启动 Spring 上下文</b>：{@code SubmitRateLimiter} 的时间来自可注入的
 * {@link Clock}，故窗口滚动可以用「拨钟」验证，不必 {@code Thread.sleep}——后者会让用例
 * 又慢又不稳（CI 上抖动即失败）。
 */
class SubmitRateLimiterTest {

    /** 固定起点，便于断言窗口边界。 */
    private static final Instant T0 = Instant.parse("2026-10-10T00:00:00Z");

    private static final int LIMIT = 10;

    private MutableClock clock;
    private SubmitRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        limiter = SubmitRateLimiter.withClock(LIMIT, clock);
    }

    // -------------------------------------------------------------------------
    // 基本阈值
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("同一 key 前 10 次放行、第 11 次起拒绝（同一 IP ≤10 次/分钟）")
    void allowsUpToLimitThenRejects() {
        for (int i = 1; i <= LIMIT; i++) {
            assertThat(limiter.tryAcquire("203.0.113.7"))
                    .as("第 %d 次应当放行", i)
                    .isTrue();
        }
        assertThat(limiter.tryAcquire("203.0.113.7"))
                .as("第 %d 次应当被拒", LIMIT + 1)
                .isFalse();
    }

    @Test
    @DisplayName("窗口滚动后计数复位（1 分钟后重新可用）")
    void windowRolloverResetsCounter() {
        for (int i = 0; i < LIMIT; i++) {
            limiter.tryAcquire("203.0.113.7");
        }
        assertThat(limiter.tryAcquire("203.0.113.7")).isFalse();

        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("203.0.113.7"))
                .as("新窗口开始，应重新放行")
                .isTrue();
    }

    @Test
    @DisplayName("窗口内最后一刻仍受限；差 1 毫秒进入新窗口则放行")
    void windowBoundaryIsExclusiveAtStart() {
        for (int i = 0; i < LIMIT; i++) {
            limiter.tryAcquire("k");
        }
        clock.advance(Duration.ofMinutes(1).minusMillis(1));
        assertThat(limiter.tryAcquire("k"))
                .as("窗口尚未结束，仍应被拒")
                .isFalse();

        clock.advance(Duration.ofMillis(1));
        assertThat(limiter.tryAcquire("k"))
                .as("窗口刚好滚动，应放行")
                .isTrue();
    }

    // -------------------------------------------------------------------------
    // key 隔离与降级
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("不同 key 互不影响（一个 IP 被限流不影响其它 IP）")
    void keysAreIndependent() {
        for (int i = 0; i < LIMIT; i++) {
            limiter.tryAcquire("203.0.113.7");
        }
        assertThat(limiter.tryAcquire("203.0.113.7")).isFalse();
        assertThat(limiter.tryAcquire("198.51.100.9"))
                .as("另一个 IP 不受影响")
                .isTrue();
        assertThat(limiter.trackedKeyCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("拿不到客户端标识时【放行】（fail open），而不是把所有人算成同一个 key")
    void missingKeyFailsOpen() {
        for (int i = 0; i < LIMIT * 3; i++) {
            assertThat(limiter.tryAcquire(null)).as("null key 第 %d 次", i + 1).isTrue();
            assertThat(limiter.tryAcquire("   ")).as("空白 key 第 %d 次", i + 1).isTrue();
        }
        assertThat(limiter.trackedKeyCount())
                .as("放行的请求不应被计数，否则会凭空积累 key")
                .isZero();
    }

    // -------------------------------------------------------------------------
    // 计数不变量
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("被拒的请求【也】计数：否则攻击者可恒定超限、永远恰好擦边通过")
    void rejectedRequestsStillCount() {
        for (int i = 0; i < LIMIT; i++) {
            limiter.tryAcquire("k");
        }
        // 再打 100 次全部被拒
        for (int i = 0; i < 100; i++) {
            assertThat(limiter.tryAcquire("k")).isFalse();
        }
        // 窗口滚动前不放行
        assertThat(limiter.tryAcquire("k")).isFalse();
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("k")).isTrue();
    }

    @Test
    @DisplayName("清理任务移除过期窗口，内存不会随访问过的 IP 无限增长")
    void purgeRemovesExpiredWindows() {
        limiter.tryAcquire("a");
        limiter.tryAcquire("b");
        assertThat(limiter.trackedKeyCount()).isEqualTo(2);

        assertThat(limiter.purgeExpiredWindows())
                .as("窗口尚未过期，不应清理任何 key")
                .isZero();

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.purgeExpiredWindows()).isEqualTo(2);
        assertThat(limiter.trackedKeyCount()).isZero();
    }

    // -------------------------------------------------------------------------
    // 已知取舍（有意写成用例，把限制摆在明面上）
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("⚠️ 已知取舍：相邻窗口交接处可短时翻倍，本用例固化该行为")
    void fixedWindowAllowsBurstAtWindowBoundaryThisIsAnAcceptedTradeoff() {
        // 窗口【从该 key 的第一次请求开始计时】，不按墙钟对齐——所以下面的时间点都相对 T0。
        for (int i = 0; i < LIMIT; i++) {
            assertThat(limiter.tryAcquire("k")).isTrue();
        }

        // 窗口 A 还有 1 秒到期：仍被拒
        clock.advance(Duration.ofSeconds(59));
        assertThat(limiter.tryAcquire("k"))
                .as("窗口 A 尚未到期，仍应被拒")
                .isFalse();

        // 再走 1 秒：窗口 A 到期，窗口 B 从这一刻重新计数
        clock.advance(Duration.ofSeconds(1));
        for (int i = 0; i < LIMIT; i++) {
            assertThat(limiter.tryAcquire("k"))
                    .as("窗口 B 重新计数，立刻又能通过 %d 次", LIMIT)
                    .isTrue();
        }

        // 结论：从「A 的最后一刻」到「B 的开头」，同一 key 可连续通过接近 2×LIMIT 次。
        // 本用例不是「期望的行为」，而是把 Q-8 指定的【最小版本】的已知缺点固定下来：
        // 若将来改成滑动窗口／令牌桶，本用例会失败，从而提醒同步更新
        // SubmitRateLimiter 的类注释与 §8.10 的登记。
    }

    // -------------------------------------------------------------------------
    // 可控时钟
    // -------------------------------------------------------------------------

    /** 可拨动的 UTC 时钟（测试专用）。 */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
