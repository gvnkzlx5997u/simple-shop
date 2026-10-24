package com.simpleshop.common.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
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
 * 进程内固定窗口限流，按「用途 + 客户端标识」计数。
 *
 * <p>窗口从该键的第一次请求开始计时，时长一分钟。固定窗口在窗口交界处允许短时翻倍，
 * 这是已知且可接受的取舍；限流用于缓解刷量，队列上限是兜底。
 *
 * <p>拿不到客户端标识时放行并告警（fail open），避免代理配置异常导致全站被误拦。
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
        SUBMIT_INTENTION
    }

    private final Map<Scope, Integer> limits;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiter(AppProperties properties) {
        this(defaultLimits(properties), Clock.systemUTC());
    }

    private RateLimiter(Map<Scope, Integer> limits, Clock clock) {
        this.limits = Map.copyOf(limits);
        this.clock = clock;
    }

    static RateLimiter withClock(Map<Scope, Integer> limits, Clock clock) {
        return new RateLimiter(limits, clock);
    }

    private static Map<Scope, Integer> defaultLimits(AppProperties properties) {
        EnumMap<Scope, Integer> limits = new EnumMap<>(Scope.class);
        limits.put(Scope.REGISTER, properties.getRateLimit().getRegisterPerMinute());
        limits.put(Scope.LOGIN, properties.getRateLimit().getLoginPerMinute());
        limits.put(Scope.SUBMIT_INTENTION, properties.getRateLimit().getSubmitIntentionPerMinute());
        return limits;
    }

    private record Window(Instant startedAt, int count) {
    }

    /**
     * 记录一次尝试并判定是否放行。被拒绝的请求同样计数。
     *
     * @param scope     用途
     * @param clientKey 客户端标识，通常是来源 IP
     * @return 是否放行；标识为空时放行
     */
    public boolean tryAcquire(Scope scope, String clientKey) {
        if (scope == null) {
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
