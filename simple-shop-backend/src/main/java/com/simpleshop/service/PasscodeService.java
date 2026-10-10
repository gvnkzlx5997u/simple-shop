package com.simpleshop.service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.simpleshop.persistence.repository.IntentionRepository;

/**
 * 口令码的生成、归一化与格式判定（方案 §3.3.7、{@code FR-14}／{@code FR-22}）。
 *
 * <pre>
 * String        generateUnique();            // 12 位 [A-Z0-9]
 * static String normalize(String raw);       // trim + toUpperCase
 * static boolean isWellFormed(String raw);   // 归一化后须匹配 ^[A-Z0-9]{12}$
 * </pre>
 *
 * <h2>口径（§3.3.7）</h2>
 * <table border="1">
 *   <caption>逐项与依据</caption>
 *   <tr><th>项</th><th>取值</th><th>依据</th></tr>
 *   <tr><td>字符集</td><td>{@code A–Z} + {@code 0–9}（36 个），<b>不排除</b>易混字符 {@code 0/O/1/I}</td>
 *       <td>{@code C-16}、{@code DEC-07}；{@code R12-06} 已接受该可读性取舍</td></tr>
 *   <tr><td>长度</td><td><b>12</b>（<b>不得缩短</b>，见下）</td><td>{@code C-16}、{@code BR-25}</td></tr>
 *   <tr><td>生成</td><td>{@link SecureRandom}；<b>不用</b> {@code UUID} 截断</td>
 *       <td>UUID 截断的字符分布与可读性都更差</td></tr>
 *   <tr><td>唯一性</td><td>数据库 {@code uk_intention_token} 是<b>唯一防线</b>；生成时先查重</td>
 *       <td>{@code S7-02}</td></tr>
 *   <tr><td>归一化</td><td>接收侧与写入侧<b>都</b> {@code trim + toUpperCase}</td>
 *       <td>{@code R12-06}、{@code 10-G}、{@code DEC-DB-08}；<b>不得依赖</b> {@code utf8mb4_unicode_ci} 的大小写不敏感</td></tr>
 *   <tr><td>日志</td><td><b>口令码绝不出现在任何日志中</b></td><td>{@code 12-H}；{@code Intention.toString()} 已刻意省略该列</td></tr>
 * </table>
 *
 * <h2>⚠️ 为什么 12 位是「不可缩短」的硬前提</h2>
 * <p>{@code R12-03} 已登记「口令码查询<b>不做</b>错误次数限流／锁定」（{@code DEC-12}、{@code NFR-15}）——
 * 该取舍<b>成立的前提是码长保持 12 位</b>：{@code 36¹²} ≈ 4.7×10¹⁸，穷举不现实。
 * <p>若将来缩短长度，<b>必须同步启用错误次数限流</b>，否则该风险条目不再成立
 * （原 {@code BR-34} 已删除、编号保留不复用，该前提由 {@code R12-03} 承载）。
 * 这条前提随交付文档移交（{@code AC-29}）。本类把 {@link #LENGTH} 显式作为常量，
 * 正是为了让「改长度」这件事必须动一处显眼的声明。
 *
 * <h2>⚠️ 归一化必须用 {@link Locale#ROOT}</h2>
 * <p>{@code "i".toUpperCase()} 在<b>土耳其语区域</b>会得到 {@code "İ"}（带点的 I），
 * 而不是 {@code "I"}——Java 的 {@code String.toUpperCase()} <b>无参</b>版本用的是
 * <b>默认区域</b>。那会让「用户输入含小写 {@code i} 的正确口令码」在土耳其语环境
 * （本机或容器若把 {@code LANG} 设成 {@code tr_TR}）下<b>静默查不到</b>。
 * 故此处一律显式传 {@link Locale#ROOT}，并有专门用例钉住
 * （{@code PasscodeServiceTest} 会临时改默认区域再断言）。
 */
@Service
public class PasscodeService {

    private static final Logger log = LoggerFactory.getLogger(PasscodeService.class);

    /** 口令码长度（{@code C-16}、{@code BR-25}）。⚠️ 缩短它会同时废掉 {@code R12-03} 的取舍前提。 */
    public static final int LENGTH = 12;

    /** 字符集：大写字母 + 数字（36 个）。顺序无关紧要，{@link SecureRandom} 保证均匀。 */
    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    /**
     * 格式判定式。
     *
     * <p>§3.6 的原文是「{@code ^[A-Z0-9]{12}$}（<b>归一化后校验</b>）」——
     * 即先按归一化口径处理、再判格式。见 {@link #isWellFormed(String)}。
     */
    private static final Pattern WELL_FORMED = Pattern.compile("^[A-Z0-9]{" + LENGTH + "}$");

    /** 生成时的查重重试上限（§3.3.7「上限 5 次」）。 */
    private static final int MAX_GENERATE_ATTEMPTS = 5;

    private final IntentionRepository intentionRepository;
    private final SecureRandom random;

    /**
     * 生产用构造器。
     *
     * <p>⚠️ 这里显式写 {@code @Autowired}：本类另有<b>一个包内可见的重载</b>（注入随机源，供测试
     * 强制制造码冲突）。Spring 遇到多个构造器且无 {@code @Autowired} 时会因找不到默认构造器而失败，
     * 故必须显式指明用哪一个。
     */
    @Autowired
    public PasscodeService(IntentionRepository intentionRepository) {
        this(intentionRepository, new SecureRandom());
    }

    /**
     * 供测试注入可控随机源。
     *
     * <p>存在理由：{@code generateUnique} 的<b>冲突重试与耗尽分支</b>在真实随机下
     * 概率约 {@code 2×10⁻¹⁹}，永远跑不到——没有这个接缝，那两段代码就是<b>不可验证</b>的。
     * 测试用一个固定输出同一码的 {@link SecureRandom} 子类即可走通两条分支。
     */
    PasscodeService(IntentionRepository intentionRepository, SecureRandom random) {
        this.intentionRepository = intentionRepository;
        this.random = random;
    }

    // -------------------------------------------------------------------------
    // 生成
    // -------------------------------------------------------------------------

    /**
     * 生成一个库中尚不存在的口令码。
     *
     * <p>先随机生成、再 {@code findByToken} 查重，命中则重试（最多 {@value #MAX_GENERATE_ATTEMPTS} 次）。
     *
     * <h2>⚠️ 唯一性的真正保证是数据库唯一索引，不是这里的查重</h2>
     * <p>查重存在竞态窗口（两次并发提交可能各自查到「不存在」）。方案 §3.3.7 要求
     * 「并捕获 {@code DataIntegrityViolationException} 做一次重试」，本实现
     * <b>有意不在本类内做该重试</b>，理由是一条 Java/Spring 的硬约束：
     * <blockquote>
     * 唯一约束冲突发生在<b>提交/刷写</b>时，一旦抛出，当前事务已被标记为
     * <b>rollback-only</b>，<b>无法</b>在同一事务里「换个码再插一次」——
     * 那样只会在提交时得到 {@code UnexpectedRollbackException}。
     * </blockquote>
     * <p>要在同一业务动作里真的重试，必须把插入放到
     * {@code REQUIRES_NEW} 的独立事务（从而引入「外层序列号已消耗、内层却回滚」的补偿问题），
     * 代价明显大于收益：命中概率约为 {@code 1/36¹²} ≈ {@code 2×10⁻¹⁹}，
     * 而<b>数据一致性并不依赖这个重试</b>——唯一索引会挡住重复，调用方得到一个明确的失败，
     * 不会产生两条同码的意向行（即「失败安全」而不是「静默错误」）。
     *
     * <p>该取舍已登记在实现过程记录（S5 的「发现的问题」）。
     *
     * @return 12 位口令码（大写）
     * @throws IllegalStateException 连续 {@value #MAX_GENERATE_ATTEMPTS} 次都撞到已存在的码
     *         ——在 36¹² 的空间里这实际不可能，出现即说明数据异常（如被灌入了规律性的码）
     */
    public String generateUnique() {
        for (int attempt = 1; attempt <= MAX_GENERATE_ATTEMPTS; attempt++) {
            String candidate = randomPasscode();
            if (intentionRepository.findByToken(candidate).isEmpty()) {
                return candidate;
            }
            // ⚠️ 只记「第几次撞了」，绝不记码值（12-H）
            log.warn("passcode collision on attempt {} of {}", attempt, MAX_GENERATE_ATTEMPTS);
        }
        throw new IllegalStateException(
                "failed to generate a unique passcode after " + MAX_GENERATE_ATTEMPTS
                        + " attempts; the token space is 36^" + LENGTH + ", so this indicates abnormal data");
    }

    /** 逐位独立取字符，保证分布均匀（不做 UUID 截断）。 */
    private String randomPasscode() {
        char[] chars = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            chars[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(chars);
    }

    // -------------------------------------------------------------------------
    // 归一化与格式判定（纯函数，无需 Spring 容器）
    // -------------------------------------------------------------------------

    /**
     * 归一化：{@code trim + toUpperCase}。
     *
     * <p>Service 在<b>接收侧与写入侧</b>都要用（{@code DEC-DB-08}）：写入侧保证库内恒为大写，
     * 接收侧让「用户输入小写或带首尾空格」仍能查到。
     * <p>{@code null} 原样返回（调用方自行决定如何处置）。
     *
     * @param raw 用户原样输入
     * @return 归一化后的口令码；{@code raw} 为 {@code null} 时返回 {@code null}
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        // ⚠️ 必须显式 Locale.ROOT：无参 toUpperCase 用默认区域，土耳其语下 "i" → "İ"（见类注释）
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 格式判定：{@code ^[A-Z0-9]{12}$}。
     *
     * <h2>⚠️ 本方法<b>内部先归一化</b>，可以直接喂用户原样输入</h2>
     * <p>§3.6 对该字段的原文是「{@code ^[A-Z0-9]{12}$}（<b>归一化后校验</b>）」。
     * 本方法把「归一化」这一步<b>并进来</b>，而不是要求调用方先调 {@link #normalize(String)}：
     * 否则调用方一旦忘记（很容易忘），用户粘贴的 <code>" abc123def456 "</code>
     * 会被判成「格式不符」（{@code B11-09}），而实际上查库时它又是能命中的——
     * 同一个输入在两处得到相反结论，排查起来极其费解。
     *
     * <p>因此本方法回答的是「<b>这段原样输入是否构成一个格式合法的口令码</b>」，
     * 语义上更接近调用方想问的问题。
     *
     * @param raw 用户原样输入
     * @return 归一化后是否匹配 12 位 {@code [A-Z0-9]}；{@code null}／空白返回 {@code false}
     */
    public static boolean isWellFormed(String raw) {
        String normalized = normalize(raw);
        return normalized != null && WELL_FORMED.matcher(normalized).matches();
    }
}
