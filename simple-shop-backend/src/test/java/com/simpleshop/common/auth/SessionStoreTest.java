package com.simpleshop.common.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionStoreTest {

    private final AuthPrincipal seller = new AuthPrincipal(AuthRole.SELLER, "SELLER-1", "seller");
    private final AuthPrincipal buyer = new AuthPrincipal(AuthRole.BUYER, "BUYER-1", "buyer");

    @Test
    @DisplayName("创建后可用对应角色校验，并返回同一主体")
    void createsAndValidates() {
        SessionStore store = SessionStore.withTimeout(Duration.ofMinutes(30), Duration.ZERO);
        SessionStore.Session session = store.create(seller);

        assertThat(store.validate(session.token(), AuthRole.SELLER)).contains(seller);
        assertThat(store.activeCount()).isEqualTo(1);
        assertThat(store.activeCount(AuthRole.SELLER)).isEqualTo(1);
    }

    @Test
    @DisplayName("买家令牌不能用于卖家接口，反之亦然")
    void rejectsWrongRole() {
        SessionStore store = SessionStore.withTimeout(Duration.ofMinutes(30), Duration.ZERO);
        SessionStore.Session buyerSession = store.create(buyer);

        assertThat(store.validate(buyerSession.token(), AuthRole.SELLER)).isEmpty();
        assertThat(store.validate(buyerSession.token(), AuthRole.BUYER)).contains(buyer);
    }

    @Test
    @DisplayName("退出后令牌立即失效")
    void removeInvalidatesToken() {
        SessionStore store = SessionStore.withTimeout(Duration.ofMinutes(30), Duration.ZERO);
        SessionStore.Session session = store.create(seller);

        store.remove(session.token());

        assertThat(store.validate(session.token(), AuthRole.SELLER)).isEmpty();
    }

    @Test
    @DisplayName("改密后清除该账号的全部会话，不影响他人")
    void removeAllForSubject() {
        SessionStore store = SessionStore.withTimeout(Duration.ofMinutes(30), Duration.ZERO);
        SessionStore.Session first = store.create(seller);
        SessionStore.Session second = store.create(seller);
        SessionStore.Session other = store.create(buyer);

        int removed = store.removeAllFor(AuthRole.SELLER, seller.subjectId());

        assertThat(removed).isEqualTo(2);
        assertThat(store.validate(first.token(), AuthRole.SELLER)).isEmpty();
        assertThat(store.validate(second.token(), AuthRole.SELLER)).isEmpty();
        assertThat(store.validate(other.token(), AuthRole.BUYER)).contains(buyer);
    }

    @Test
    @DisplayName("超时后令牌失效")
    void expiresAfterTimeout() {
        SessionStore store = SessionStore.withTimeout(Duration.ZERO, Duration.ZERO);
        SessionStore.Session session = store.create(seller);

        assertThat(store.validate(session.token(), AuthRole.SELLER)).isEmpty();
        assertThat(store.activeCount()).isZero();
    }

    @Test
    @DisplayName("空白令牌直接判为无效")
    void rejectsBlankToken() {
        SessionStore store = SessionStore.withTimeout(Duration.ofMinutes(30), Duration.ZERO);

        assertThat(store.validate(null, AuthRole.SELLER)).isEmpty();
        assertThat(store.validate("  ", AuthRole.SELLER)).isEmpty();
    }
}
