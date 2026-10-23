package com.simpleshop.common.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.simpleshop.common.config.AppProperties;

/**
 * 进程内会话存储。
 *
 * <p>后端按单副本部署，会话保存在进程内；重启或改密后会话失效是可接受的。
 * 若改为多副本部署，必须换成共享存储。
 *
 * <p>会话记录不可变，校验时用 {@link ConcurrentMap#computeIfPresent} 原子完成
 * 「判断过期并滑动续期」，避免并发下丢失刷新导致会话提前失效。
 */
@Component
public class SessionStore {

    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

    private static final long PURGE_INTERVAL_MS = 60_000L;
    private static final int TOKEN_BYTES = 32;

    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Duration timeout;
    private final Duration timeoutTolerance;

    public SessionStore(AppProperties properties) {
        this.timeout = properties.getSession().getTimeout();
        this.timeoutTolerance = properties.getSession().getTimeoutTolerance();
    }

    public record Session(String token, AuthPrincipal principal, Instant lastAccessAt, Instant expireAt) {

        Session touched(Instant now, Duration timeout) {
            return new Session(token, principal, now, now.plus(timeout));
        }
    }

    public Session create(AuthPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        String token = newToken();
        Instant now = Instant.now();
        Session session = new Session(token, principal, now, now.plus(timeout));
        sessions.put(token, session);
        log.debug("session created: role={} subjectId={}", principal.role(), principal.subjectId());
        return session;
    }

    /**
     * 校验令牌并滑动续期。
     *
     * @param token        请求头中解析出的令牌
     * @param expectedRole 期望的角色；角色不匹配视为无效
     * @return 已认证主体；无效、过期或角色不匹配时为空
     */
    public Optional<AuthPrincipal> validate(String token, AuthRole expectedRole) {
        if (token == null || token.isBlank() || expectedRole == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        Session current = sessions.computeIfPresent(token, (key, existing) -> {
            if (existing.principal().role() != expectedRole) {
                return existing;
            }
            return isExpired(existing, now) ? null : existing.touched(now, timeout);
        });
        if (current == null
                || current.principal().role() != expectedRole
                || isExpired(current, now)) {
            return Optional.empty();
        }
        return Optional.of(current.principal());
    }

    /** 退出登录，幂等。 */
    public void remove(String token) {
        if (token != null && !token.isBlank()) {
            sessions.remove(token);
        }
    }

    /** 使某个账号的全部会话失效，用于改密。 */
    public int removeAllFor(AuthRole role, String subjectId) {
        if (role == null || subjectId == null || subjectId.isBlank()) {
            return 0;
        }
        int before = sessions.size();
        sessions.values().removeIf(session -> session.principal().role() == role
                && subjectId.equals(session.principal().subjectId()));
        return revokeCount(before, role + ":" + subjectId);
    }

    /** 使某角色的全部会话失效。 */
    public int removeAllFor(AuthRole role) {
        if (role == null) {
            return 0;
        }
        int before = sessions.size();
        sessions.values().removeIf(session -> session.principal().role() == role);
        return revokeCount(before, role.name());
    }

    public int activeCount() {
        return sessions.size();
    }

    public int activeCount(AuthRole role) {
        if (role == null) {
            return 0;
        }
        return (int) sessions.values().stream()
                .filter(session -> session.principal().role() == role)
                .count();
    }

    @Scheduled(fixedDelay = PURGE_INTERVAL_MS)
    public int purgeExpired() {
        Instant now = Instant.now();
        int before = sessions.size();
        sessions.values().removeIf(session -> isExpired(session, now));
        int purged = before - sessions.size();
        if (purged > 0) {
            log.debug("purged {} expired session(s)", purged);
        }
        return purged;
    }

    private int revokeCount(int before, String scope) {
        int removed = before - sessions.size();
        log.info("sessions revoked for {}: removed={}", scope, removed);
        return removed;
    }

    private boolean isExpired(Session session, Instant now) {
        return !now.isBefore(session.expireAt().plus(timeoutTolerance));
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 供测试构造固定时长的存储实例。 */
    public static SessionStore withTimeout(Duration timeout, Duration tolerance) {
        AppProperties properties = new AppProperties();
        properties.getSession().setTimeout(Objects.requireNonNull(timeout));
        properties.getSession().setTimeoutTolerance(Objects.requireNonNull(tolerance));
        return new SessionStore(properties);
    }
}
