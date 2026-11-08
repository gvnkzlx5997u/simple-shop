package com.simpleshop.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static final RateLimiter.Scope SCOPE = RateLimiter.Scope.REGISTER;

    @Test
    @DisplayName("窗口内放行到上限，之后拒绝")
    void allowsUpToLimit() {
        RateLimiter limiter = RateLimiter.withClock(Map.of(SCOPE, 2), new MutableClock());

        assertThat(limiter.tryAcquire(SCOPE, "1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire(SCOPE, "1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire(SCOPE, "1.1.1.1")).isFalse();
    }

    @Test
    @DisplayName("不同用途与不同客户端分别计数")
    void countsPerScopeAndClient() {
        RateLimiter limiter = RateLimiter.withClock(
                Map.of(SCOPE, 1, RateLimiter.Scope.LOGIN, 1), new MutableClock());

        assertThat(limiter.tryAcquire(SCOPE, "1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire(RateLimiter.Scope.LOGIN, "1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire(SCOPE, "2.2.2.2")).isTrue();
        assertThat(limiter.tryAcquire(SCOPE, "1.1.1.1")).isFalse();
    }

    @Test
    @DisplayName("窗口结束后重新计数")
    void resetsAfterWindow() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = RateLimiter.withClock(Map.of(SCOPE, 1), clock);

        assertThat(limiter.tryAcquire(SCOPE, "1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire(SCOPE, "1.1.1.1")).isFalse();

        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire(SCOPE, "1.1.1.1")).isTrue();
    }

    @Test
    @DisplayName("拿不到客户端标识时放行")
    void failsOpenWithoutClientKey() {
        RateLimiter limiter = RateLimiter.withClock(Map.of(SCOPE, 1), new MutableClock());

        assertThat(limiter.tryAcquire(SCOPE, null)).isTrue();
        assertThat(limiter.tryAcquire(SCOPE, "  ")).isTrue();
        assertThat(limiter.trackedKeyCount()).isZero();
    }

    @Test
    @DisplayName("过期窗口会被清理")
    void purgesExpiredWindows() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = RateLimiter.withClock(Map.of(SCOPE, 1), clock);
        limiter.tryAcquire(SCOPE, "1.1.1.1");

        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.purgeExpiredWindows()).isEqualTo(1);
        assertThat(limiter.trackedKeyCount()).isZero();
    }

    @Test
    @DisplayName("总开关关闭时一律放行，也不留下计数")
    void disabledSwitchAllowsEverything() {
        RateLimiter limiter = RateLimiter.disabled(new MutableClock());

        for (int attempt = 0; attempt < 100; attempt++) {
            assertThat(limiter.tryAcquire(RateLimiter.Scope.LOGIN, "1.1.1.1")).isTrue();
        }
        assertThat(limiter.trackedKeyCount()).isZero();
    }

    @Test
    @DisplayName("登录键由账号与地址组成，同一地址的不同账号分开计数")
    void loginKeyCombinesAccountAndClient() {
        assertThat(RateLimiter.loginKey(" Alice ", "1.1.1.1")).isEqualTo("alice@1.1.1.1");
        assertThat(RateLimiter.loginKey("alice", null)).isEqualTo("alice@");
        assertThat(RateLimiter.loginKey(null, "1.1.1.1")).isEqualTo("@1.1.1.1");
        assertThat(RateLimiter.loginKey("  ", " ")).isNull();

        RateLimiter limiter = RateLimiter.withClock(
                Map.of(RateLimiter.Scope.LOGIN, 1), new MutableClock());
        assertThat(limiter.tryAcquire(
                RateLimiter.Scope.LOGIN, RateLimiter.loginKey("alice", "1.1.1.1"))).isTrue();
        assertThat(limiter.tryAcquire(
                RateLimiter.Scope.LOGIN, RateLimiter.loginKey("bob", "1.1.1.1"))).isTrue();
        assertThat(limiter.tryAcquire(
                RateLimiter.Scope.LOGIN, RateLimiter.loginKey("alice", "1.1.1.1"))).isFalse();
    }

    private static final class MutableClock extends Clock {

        private Instant instant = Instant.parse("2026-10-23T00:00:00Z");

        private void advance(Duration duration) {
            instant = instant.plus(duration);
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
