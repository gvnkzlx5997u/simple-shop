package com.simpleshop.session;

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

import com.simpleshop.config.AppProperties;

/**
 * 会话存储：<b>进程内</b>实现（方案 §5）。对应 {@code C-07} 的「session + token」。
 *
 * <h2>为什么放在进程内、不引入缓存（✅ 已定 Q-3）</h2>
 * <p>部署形态为<b>容器化</b>（后端 / 数据库 / 卖家前端三个容器），但<b>后端是单容器</b>。
 * 会话共享问题的根源是「同一份内存被多个进程各持一份」，只有<b>多副本 / 负载均衡</b>才会出现；
 * 单容器下进程内 Map 就是唯一且正确的会话视图。
 * 再加上系统<b>只有一个管理员账号</b>（{@code FR-01}、{@code O-10}），会话数量正常就是 1 个，
 * 容量与并发压力都不存在。因此不引入 Redis（{@code C-04} 亦已排除缓存组件）。
 *
 * <p><b>容器重启会丢会话</b>（用户需重新登录）——这是<b>可接受、甚至更可取</b>的：
 * 它符合 {@code DEC-17}（改密后全部会话失效）的口径，也符合 {@code NFR-04}「30 分钟无操作退出」
 * 的短生命周期设计意图。为「重启后仍保持登录」而持久化会话，收益极低。
 *
 * <p><b>⚠️ 复核触发条件</b>（方案 §5.4）：若后端改为<b>多容器 / 多副本 / 负载均衡</b>，
 * 必须改为共享存储（Redis 或会话表），且须同时复核 {@code C-04} 与 {@code 9-O}，走变更记录。
 *
 * <h2>并发正确性（本类最容易写错的地方）</h2>
 * <p>{@link #validate(String)} 需要「校验 + 滑动刷新 {@code lastAccessAt}」，
 * 是典型的<b>读—改—写</b>复合操作。若写成 {@code get(token)} 后直接改对象字段，
 * 会有可见性问题、并发下还会 lost update 导致会话<b>提前</b>失效。
 *
 * <p>因此本类：
 * <ol>
 *   <li>{@link Session} 是 <b>immutable record</b>（刷新即产生新实例）；</li>
 *   <li>{@link #validate(String)} 用 {@link ConcurrentMap#computeIfPresent} 完成
 *       「判断过期 → 删除或替换」——该操作对同一 key 是<b>原子</b>的；</li>
 *   <li><b>不</b>使用 {@code Collections.synchronizedMap} + 手动 {@code get}/{@code put} 组合：
 *       那只保证单个操作原子，<b>保证不了复合操作</b>。</li>
 * </ol>
 *
 * <h2>超时判定</h2>
 * <p>过期必须是<b>显式</b>的（比较 {@code lastAccessAt + timeout} 与当前时刻），
 * 不能靠「清理任务还没删掉」来隐式放行——否则已过期但未被清理的会话会被误放行。
 * 宽容窗口 {@code ±1 分钟}（{@code 12-P7}）的实现见 {@link #isExpired}。
 */
@Component
public class SessionStore {

    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

    /**
     * 会话清理任务的固定间隔。
     *
     * <p><b>为什么不做成可配置</b>：{@code @Scheduled} 的 {@code fixedDelay} 要求<b>编译期常量</b>，
     * 用配置值必须引入 {@code SchedulingConfigurer} 并手工注册任务——为一个 60 秒的清理节奏
     * 增加一个配置类，属过度设计。且本系统会话数量是个位数，清理节奏不构成调优点。
     */
    private static final long PURGE_INTERVAL_MS = 60_000L;

    /** token 随机字节数：32 字节 → URL-safe Base64 约 43 字符。 */
    private static final int TOKEN_BYTES = 32;

    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Duration timeout;
    private final Duration timeoutTolerance;

    public SessionStore(AppProperties properties) {
        this.timeout = properties.getSession().getTimeout();
        this.timeoutTolerance = properties.getSession().getTimeoutTolerance();
    }

    /**
     * 会话记录（不可变）。
     *
     * <p>刻意用 {@code record}：不可变使「滑动刷新」变成「替换整条记录」，
     * 从而可以安全地放进 {@code computeIfPresent} 里，避开可变对象的可见性问题。
     *
     * @param token       会话令牌
     * @param account     所属账号（当前系统恒为 {@code seller}）
     * @param lastAccessAt 最后一次访问时刻（UTC；每次请求刷新）
     * @param expireAt    过期时刻（UTC）= {@code lastAccessAt + timeout}
     */
    public record Session(String token, String account, Instant lastAccessAt, Instant expireAt) {

        /** 以指定时刻刷新会话，产生新实例。 */
        Session touched(Instant now, Duration timeout) {
            return new Session(token, account, now, now.plus(timeout));
        }
    }

    // -------------------------------------------------------------------------
    // 核心操作
    // -------------------------------------------------------------------------

    /**
     * 创建会话并生成 token（登录时调用，{@code I11-01}）。
     *
     * @param account 登录账号
     * @return 新建的会话（含 token 与 {@code expireAt}，供 {@code I11-01} 的 {@code expire_at} 下发）
     */
    public Session create(String account) {
        String token = newToken();
        Instant now = Instant.now();
        Session session = new Session(token, account, now, now.plus(timeout));
        sessions.put(token, session);
        log.debug("session created: account={}", account);
        return session;
    }

    /**
     * 校验 token 并<b>原子地</b>滑动刷新 {@code lastAccessAt}。
     *
     * <p>三种返回 {@link Optional#empty()} 的情形（对调用方都是同一个结果：{@code 10002}）：
     * <ul>
     *   <li>token 不存在（未登录 / 已退出 / 已改密 / 容器重启）</li>
     *   <li>token 已过期（空闲超过 {@code timeout}）——同时把它<b>移除</b></li>
     *   <li>token 为 {@code null} 或空白</li>
     * </ul>
     *
     * <p><b>⚠️ 不要把「过期判定」交给清理任务</b>：清理任务只是防内存泄漏的兜底，
     * 正确性依赖本方法的显式比较（否则已过期但未清理的会话会被放行）。
     *
     * @param token 请求头 {@code Authorization: Bearer <token>} 中解析出的 token
     * @return 刷新后的会话；无效/过期时为 {@link Optional#empty()}
     */
    public Optional<Session> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        // computeIfPresent 对同一 key 原子：到期则返回 null（= 删除 key），否则替换为刷新后的实例
        Session current = sessions.computeIfPresent(token, (key, existing) -> {
            if (isExpired(existing, now)) {
                return null;   // 原子删除
            }
            return existing.touched(now, timeout);
        });
        return Optional.ofNullable(current);
    }

    /** 退出登录（{@code I11-02}）。幂等：token 不存在也不报错。 */
    public void remove(String token) {
        if (token != null && !token.isBlank()) {
            sessions.remove(token);
        }
    }

    /**
     * 使某账号的<b>全部</b>会话立即失效（改密后调用，{@code DEC-17}、{@code FR-02}）。
     *
     * <p><b>⚠️ 必须是「全部会话」而不只是当前会话</b>——这是 {@code DEC-17} 的明确口径，
     * 也是 {@code NFR-04} 的可测指标（双会话对照：A、B 登录 → A 改密 → B 的下一次请求应被拒）。
     * <p>清空是同步的，旧凭证<b>立即</b>失效（契约要求 ≤5s，实际为 0）。
     *
     * @param account 账号
     * @return 被清除的会话数（便于日志核对与测试断言）
     */
    public int removeAllFor(String account) {
        if (account == null || account.isBlank()) {
            return 0;
        }
        int before = sessions.size();
        // 按值匹配删除：会话数量为个位数，全量扫描的代价可忽略
        sessions.values().removeIf(session -> account.equals(session.account()));
        int removed = before - sessions.size();
        log.info("all sessions revoked for account={}, removed={}", account, removed);
        return removed;
    }

    /** 当前活跃会话数（供监控与测试断言）。 */
    public int activeCount() {
        return sessions.size();
    }

    /** 清理过期会话（防内存泄漏）。返回被清理的条数。 */
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

    // -------------------------------------------------------------------------
    // 内部工具
    // -------------------------------------------------------------------------

    /**
     * 是否已过期。
     *
     * <p>宽容窗口（{@code 12-P7}：±1 分钟）在此落实：把 {@code expireAt} 往后放宽
     * {@code timeoutTolerance} 再比较。<b>放宽</b>而非收紧，是为了避免「客户端在临界点被
     * 判超时」这种体验问题——契约允许 ±1 分钟的判定误差。
     */
    private boolean isExpired(Session session, Instant now) {
        return !now.isBefore(session.expireAt().plus(timeoutTolerance));
    }

    /**
     * 生成 token：{@link SecureRandom} 32 字节 → URL-safe Base64（无填充）。
     *
     * <p>为何 <b>不用 JWT</b>：JWT 无法「立即使全部会话失效」而不引入黑名单存储，
     * 与 {@code DEC-17}（改密后全部会话立即失效）直接冲突。随机会话 id 天然支持服务端撤销。
     */
    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 便于测试构造固定时长存储（不走 Spring 容器）。 */
    public static SessionStore withTimeout(Duration timeout, Duration tolerance) {
        AppProperties properties = new AppProperties();
        properties.getSession().setTimeout(Objects.requireNonNull(timeout));
        properties.getSession().setTimeoutTolerance(Objects.requireNonNull(tolerance));
        return new SessionStore(properties);
    }
}
