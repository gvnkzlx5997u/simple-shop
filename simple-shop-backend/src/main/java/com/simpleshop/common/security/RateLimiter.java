package com.simpleshop.common.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.simpleshop.common.config.AppProperties;

/**
 * 进程内固定窗口限流，按「用途 + 标识」计数。
 *
 * <p>窗口从该键的第一次请求开始计时，时长一分钟。固定窗口在窗口交界处允许短时翻倍，
 * 这是已知且可接受的取舍。限流只做保护，不是业务规则：注册与登录按账号 + 地址计，
 * 下单只给一个很高的兜底阈值，购物车与收藏不限流。
 *
 * <p>总开关是 {@code simple-shop.rate-limit.enabled}，开发与测试直接关掉，不要靠调大阈值绕过。
 *
 * <p>拿不到标识时放行并告警（fail open），避免代理配置异常导致全站被误拦。
 * 若后端改为多副本部署，计数不再准确，必须换成共享存储。
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final long PURGE_INTERVAL_MS = 60_000L;

    public enum Scope {
        REGISTER,
        LOGIN,
        ORDER
    }

    private final Map<Scope, Integer> limits;
    private final boolean enabled;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiter(AppProperties properties) {
        this(defaultLimits(properties), properties.getRateLimit().isEnabled(), Clock.systemUTC());
    }

    private RateLimiter(Map<Scope, Integer> limits, boolean enabled, Clock clock) {
        this.limits = Map.copyOf(limits);
        this.enabled = enabled;
        this.clock = clock;
    }

    static RateLimiter withClock(Map<Scope, Integer> limits, Clock clock) {
        return new RateLimiter(limits, true, clock);
    }

    static RateLimiter disabled(Clock clock) {
        return new RateLimiter(Map.of(), false, clock);
    }

    private static Map<Scope, Integer> defaultLimits(AppProperties properties) {
        EnumMap<Scope, Integer> limits = new EnumMap<>(Scope.class);
        limits.put(Scope.REGISTER, properties.getRateLimit().getRegisterPerMinute());
        limits.put(Scope.LOGIN, properties.getRateLimit().getLoginPerMinute());
        limits.put(Scope.ORDER, properties.getRateLimit().getOrderPerMinute());
        return limits;
    }

    /**
     * 登录限流键：账号 + 客户端地址。只按地址计会误伤同一出口 IP 下的多个用户，
     * 只按账号计又挡不住撞库；两侧都拿不到时返回 null，按 fail open 放行。
     */
    public static String loginKey(String account, String clientKey) {
        String normalizedAccount = account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
        String normalizedClient = clientKey == null ? "" : clientKey.trim();
        if (normalizedAccount.isEmpty() && normalizedClient.isEmpty()) {
            return null;
        }
        return normalizedAccount + "@" + normalizedClient;
    }

    private record Window(Instant startedAt, int count) {
    }

    /**
     * 记录一次尝试并判定是否放行。被拒绝的请求同样计数。
     *
     * @param scope     用途
     * @param clientKey 计数标识，通常是来源 IP 或「账号@IP」
     * @return 是否放行；总开关关闭或标识为空时放行
     */
    public boolean tryAcquire(Scope scope, String clientKey) {
        if (!enabled || scope == null) {
            return true;
        }
        if (clientKey == null || clientKey.isBlank()) {
            log.warn("rate limit skipped: client key is unavailable; allowing the request (fail open)");
            return true;
        }
        int limit = limits.getOrDefault(scope, 10);
        String key = scope.name() + "|" + clientKey;
        Instant now = clock.instant();
        Window current = windows.compute(key, (ignored, existing) -> {
            if (existing == null || !now.isBefore(existing.startedAt().plus(WINDOW))) {
                return new Window(now, 1);
            }
            return new Window(existing.startedAt(), Math.min(existing.count() + 1, limit + 1));
        });
        return current.count() <= limit;
    }

    public int trackedKeyCount() {
        return windows.size();
    }

    @Scheduled(fixedDelay = PURGE_INTERVAL_MS)
    public int purgeExpiredWindows() {
        Instant now = clock.instant();
        int before = windows.size();
        windows.values().removeIf(window -> !now.isBefore(window.startedAt().plus(WINDOW)));
        int purged = before - windows.size();
        if (purged > 0) {
            log.debug("purged {} expired rate-limit window(s)", purged);
        }
        return purged;
    }
}
