package com.simpleshop.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SessionStore} 的行为验证（方案 §5.2；{@code C-07}、{@code DEC-13}、{@code DEC-17}、{@code NFR-04}）。
 *
 * <p>用 {@link SessionStore#withTimeout} 构造极短超时，使「滑动过期」与「过期清理」
 * 能在毫秒级验证——<b>不</b>依赖 Spring 容器，因此不受 30 分钟配置的影响。
 */
class SessionStoreTest {

    /** 宽容窗口设为 0，使超时判定严格，便于断言边界。 */
    private static SessionStore strictStore(Duration timeout) {
        return SessionStore.withTimeout(timeout, Duration.ZERO);
    }

    @Test
    @DisplayName("create 产生可用的 token，validate 能校验通过")
    void createThenValidate() {
        SessionStore store = strictStore(Duration.ofMinutes(30));
        SessionStore.Session session = store.create("seller");

        assertThat(session.token()).isNotBlank();
        // URL-safe Base64 的 32 字节，去掉填充约 43 字符
        assertThat(session.token()).hasSize(43);
        assertThat(session.account()).isEqualTo("seller");
        assertThat(session.expireAt()).isAfter(session.lastAccessAt());

        assertThat(store.validate(session.token())).isPresent()
                .get().extracting(SessionStore.Session::account).isEqualTo("seller");
    }

    @Test
    @DisplayName("不同会话的 token 互不相同（随机性）")
    void tokensAreUnique() {
        SessionStore store = strictStore(Duration.ofMinutes(30));
        String a = store.create("seller").token();
        String b = store.create("seller").token();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("未知 token / null / 空白一律校验失败（对应 10002）")
    void rejectsUnknownToken() {
        SessionStore store = strictStore(Duration.ofMinutes(30));
        store.create("seller");

        assertThat(store.validate("nonexistent-token")).isEmpty();
        assertThat(store.validate(null)).isEmpty();
        assertThat(store.validate("")).isEmpty();
        assertThat(store.validate("   ")).isEmpty();
    }

    @Test
    @DisplayName("validate 滑动刷新 lastAccessAt（30 分钟无操作才过期）")
    void validateSlidesExpiry() throws Exception {
        SessionStore store = strictStore(Duration.ofMillis(300));
        SessionStore.Session created = store.create("seller");

        Thread.sleep(150);
        SessionStore.Session refreshed = store.validate(created.token()).orElseThrow();

        // 刷新后 lastAccessAt 前进、expireAt 随之延后
        assertThat(refreshed.lastAccessAt()).isAfter(created.lastAccessAt());
        assertThat(refreshed.expireAt()).isAfter(created.expireAt());

        // 再等 150ms：从「创建」算已 300ms，但从「刷新」算仅 150ms → 仍然有效
        Thread.sleep(150);
        assertThat(store.validate(created.token()))
                .as("滑动过期必须从最后一次访问起算，而不是从创建起算")
                .isPresent();
    }

    @Test
    @DisplayName("空闲超过超时时长后失效，且已被移除")
    void expiresWhenIdleTooLong() throws Exception {
        SessionStore store = strictStore(Duration.ofMillis(120));
        String token = store.create("seller").token();
        assertThat(store.validate(token)).isPresent();

        Thread.sleep(300);

        assertThat(store.validate(token)).isEmpty();
        // 过期会话应在 validate 中就被原子删除，而不是等清理任务
        assertThat(store.activeCount()).isZero();
        assertThat(store.validate(token)).isEmpty();
    }

    @Test
    @DisplayName("remove（退出登录）幂等：移除后失效，重复调用不报错")
    void removeIsIdempotent() {
        SessionStore store = strictStore(Duration.ofMinutes(30));
        String token = store.create("seller").token();

        store.remove(token);
        assertThat(store.validate(token)).isEmpty();
        // 再次移除不应抛异常
        store.remove(token);
        store.remove(null);
        store.remove("");
    }

    @Test
    @DisplayName("removeAllFor 使该账号【全部】会话立即失效（DEC-17 的核心口径）")
    void removeAllForRevokesEverySession() {
        SessionStore store = strictStore(Duration.ofMinutes(30));
        // 模拟双会话对照：同一账号在两个「浏览器」登录
        String tokenA = store.create("seller").token();
        String tokenB = store.create("seller").token();
        String other = store.create("someone-else").token();
        assertThat(store.activeCount()).isEqualTo(3);

        int removed = store.removeAllFor("seller");

        assertThat(removed).isEqualTo(2);
        assertThat(store.validate(tokenA)).as("当前会话也必须失效（不只是其它会话）").isEmpty();
        assertThat(store.validate(tokenB)).as("其它会话必须失效").isEmpty();
        assertThat(store.validate(other)).as("不得误伤其它账号").isPresent();
    }

    @Test
    @DisplayName("purgeExpired 清理过期会话，不影响有效会话")
    void purgeExpiredRemovesOnlyExpired() throws Exception {
        SessionStore store = strictStore(Duration.ofMillis(100));
        String expiring = store.create("seller").token();
        Thread.sleep(250);
        String fresh = store.create("seller").token();

        int purged = store.purgeExpired();

        assertThat(purged).isEqualTo(1);
        assertThat(store.validate(expiring)).isEmpty();
        assertThat(store.validate(fresh)).isPresent();
    }

    @Test
    @DisplayName("超时宽容窗口：宽限期内仍有效（12-P7 的 ±1 分钟）")
    void toleranceWindowKeepsSessionAlive() throws Exception {
        // 超时 100ms，宽容 500ms → 总计约 600ms 内都应有效
        SessionStore store = SessionStore.withTimeout(Duration.ofMillis(100), Duration.ofMillis(500));
        String token = store.create("seller").token();

        Thread.sleep(300);   // 已超过 timeout(100ms)，但在 timeout + tolerance(600ms) 内
        assertThat(store.validate(token))
                .as("宽容窗口内不应判超时")
                .isPresent();
    }
}
