package com.simpleshop.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.simpleshop.config.AppProperties;

/**
 * 提交意向的<b>基础限流</b>（{@code NFR-17}、{@code 12-P6}、{@code DEC-26}）。
 *
 * <p>阈值：<b>同一 IP 每分钟 ≤ 10 次</b>；超出即拒绝，前端另加 2s 防抖（{@code 12-P6}）。
 *
 * <h2>✅ 为什么落在 Service 层（已定 Q-8）</h2>
 * <p>上游 {@code 12-P6} 的「验证方式」栏写的是「接口层拦截实现」，而 {@code NFR-17} 要求
 * 「服务端限流生效」；用户已在答复中拍板：<b>本轮在 Service 层实现最小版本</b>（Q-8）。
 * 因此以 Q-8 为准——它比 {@code 12-P6} 的措辞更具体，且是更晚的决策。
 *
 * <h2>⚠️ 与另外两处策略<b>分列</b>，不得互相套用（三处独立）</h2>
 * <table border="1">
 *   <caption>三处「次数」策略互不相同</caption>
 *   <tr><th>入口</th><th>策略</th><th>依据</th></tr>
 *   <tr><td><b>提交购买意向</b>（本类）</td><td><b>限流</b>：同一 IP ≤10 次/分钟</td>
 *       <td>{@code NFR-17}、{@code 12-P6}、{@code DEC-26}</td></tr>
 *   <tr><td>卖家登录</td><td><b>不限</b>失败次数、不锁定</td><td>{@code DEC-25}、{@code NFR-03}</td></tr>
 *   <tr><td>口令码查询</td><td><b>不限</b>错误次数、不锁定</td>
 *       <td>{@code DEC-12}、{@code NFR-15}（前提：码长 12 位，见 {@link PasscodeService}）</td></tr>
 * </table>
 *
 * <h2>⚠️ 算法是【固定窗口】，它的已知缺点必须讲清楚</h2>
 * <p>窗口<b>从该 key 的第一次请求开始计时</b>、持续 1 分钟（<b>不</b>按墙钟对齐到整分钟）。
 * 窗口内计数，超过阈值即拒绝。
 *
 * <p>已知缺点：<b>相邻窗口的交接处可短时翻倍</b>——在 {@code T0} 打满 10 次后，
 * 到 {@code T0+60s} 新窗口立刻又能通过 10 次，于是「一秒内 10 次、紧邻的前一分钟内共 20 次」
 * 是可能的。要消除它需改用滑动窗口或令牌桶（每个 key 保存时间序列）。
 * <p>之所以接受：Q-8 明确要的是<b>最小版本</b>；限流是<b>缓解措施</b>而不是正确性约束
 * （{@code R12-10} 已登记「只做基础限流」这一已接受风险，兜底是队列上限 1000 条）。
 * 该缺点有一条专门用例固化其行为（{@code SubmitRateLimiterTest}），
 * 将来若改成滑动窗口，那条用例会失败，提醒同步更新本注释与 §8.10 的登记。
 *
 * <h2>⚠️ 单后端容器才成立</h2>
 * <p>与 {@code SessionStore} 同一前提（方案 §5）：计数器在<b>进程内</b>。
 * 若后端改为<b>多容器／多副本／负载均衡</b>，每个副本各有一份计数，
 * 实际阈值会变成 {@code 10 × 副本数}——<b>必须</b>改为共享存储，
 * 并同时复核 {@code C-04}（不使用缓存组件）与方案 §5.4。
 *
 * <h2>⚠️ 拿不到客户端标识时【放行】而不是拒绝</h2>
 * <p>若调用方给不出 key（{@code null}／空白），本类<b>放行并告警</b>（fail open）。
 * 反过来（fail closed）更糟：当反向代理配置变化导致取不到 IP 时，
 * 所有买家会被算成<b>同一个 key</b>而集体被限流——一次配置失误变成全站不可用。
 * 限流失效只是失去一层缓解，而误拦是实打实的功能故障。
 */
@Component
public class SubmitRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SubmitRateLimiter.class);

    /** 窗口长度：1 分钟（{@code 12-P6}）。 */
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /**
     * 过期窗口的清理间隔。
     *
     * <p>与 {@code SessionStore} 同理：{@code @Scheduled} 的 {@code fixedDelay} 要求
     * <b>编译期常量</b>，做成可配置就得引入 {@code SchedulingConfigurer}，属过度设计。
     */
    private static final long PURGE_INTERVAL_MS = 60_000L;

    private final int limitPerMinute;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * 生产用构造器。
     *
     * <p>⚠️ 这里显式写 {@code @Autowired}：本类另有<b>一个私有重载</b>（注入可控时钟，供测试拨动窗口）。
     * 容器遇到<b>多个</b>构造器且无 {@code @Autowired} 时，会去找无参构造器，找不到就
     * <b>启动失败</b>（{@code No default constructor found}）——这不是理论风险：
     * 本类的测试就是先以「整个 Spring 上下文起不来」的形式把这个缺陷暴露出来的。
     */
    @Autowired
    public SubmitRateLimiter(AppProperties properties) {
        this(properties.getRateLimit().getSubmitIntentionPerMinute(), Clock.systemUTC());
    }

    private SubmitRateLimiter(int limitPerMinute, Clock clock) {
        this.limitPerMinute = limitPerMinute;
        this.clock = clock;
    }

    /**
     * 供测试注入可控时钟（窗口滚动是时间相关行为，靠 {@code Thread.sleep} 验证会又慢又不稳）。
     *
     * @param limitPerMinute 阈值
     * @param clock          时间源
     */
    static SubmitRateLimiter withClock(int limitPerMinute, Clock clock) {
        return new SubmitRateLimiter(limitPerMinute, clock);
    }

    /**
     * 窗口内的计数（不可变）。
     *
     * @param startedAt 本窗口的起点
     * @param count     窗口内已受理（含被拒）的请求数
     */
    private record Window(Instant startedAt, int count) {
    }

    /**
     * 记一次提交尝试并判定是否放行。
     *
     * <p><b>⚠️ 必须先于任何数据库操作调用</b>：它是纯内存判定，放在最前面可以
     * 让超限请求完全不触碰序号表行锁——否则刷量会把行锁变成瓶颈，限流反而放大伤害。
     *
     * <p><b>⚠️ 被拒的请求也会计数</b>：否则「被拒不影响计数」会让攻击者恒定超限，
     * 每次都恰好擦边通过（计数永远停在阈值）。
     *
     * @param clientKey 客户端标识（本轮由调用方传请求来源 IP）；{@code null}／空白时放行
     * @return {@code true} 放行；{@code false} 超限
     */
    public boolean tryAcquire(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            log.warn("submit rate limit skipped: client key is unavailable; allowing the request (fail open)");
            return true;
        }
        Instant now = clock.instant();
        Window current = windows.compute(clientKey, (key, existing) -> {
            if (existing == null || !now.isBefore(existing.startedAt().plus(WINDOW))) {
                return new Window(now, 1);   // 新窗口
            }
            // 计数封顶在 limit+1：既不影响判定，又不让计数在窗口内无限增长
            return new Window(existing.startedAt(), Math.min(existing.count() + 1, limitPerMinute + 1));
        });
        return current.count() <= limitPerMinute;
    }

    /** 当前被跟踪的客户端数（供监控与测试断言内存不会无限增长）。 */
    public int trackedKeyCount() {
        return windows.size();
    }

    /**
     * 清理已过期的窗口，防止「访问过的 IP 永不释放」导致内存缓慢增长。
     *
     * @return 被清理的 key 数
     */
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
