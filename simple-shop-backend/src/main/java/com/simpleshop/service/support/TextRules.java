package com.simpleshop.service.support;

/**
 * 「统一文本规则」的公共实现（{@code §9.2.5}、{@code C-15}、{@code NFR-20}）。
 *
 * <h2>为什么单独抽出来</h2>
 * <p>§3.6 的统一文本规则适用于<b>四个</b>字段：{@code goods.name}、{@code goods.description}、
 * {@code intentions.name}、{@code fail_reason}（S6）。若每个校验器各写一份
 * 「拒绝 {@code <}/{@code >}」「长度按码点计」的实现，就会出现四处需要同步修改的副本——
 * 而这类规则恰恰是<b>安全规则</b>（{@code NFR-20} 的 XSS 第一层），漏改一处就是一条绕过路径。
 *
 * <p>本类只放<b>与字段无关</b>的纯判定；每个字段的必填性、长度上限、是否允许换行
 * 仍由各自的校验器决定（见 {@link GoodsValidator}、{@link IntentionValidator}）。
 *
 * <h2>⚠️ 长度按【码点】计，不按 {@link String#length()}</h2>
 * <p>契约说的是「≤ N <b>字符</b>」，而库列是 MySQL {@code varchar(N)}——utf8mb4 下限的也是
 * <b>N 个字符</b>。用 {@code length()}（UTF-16 单元数）会把一个 emoji 算成 2，
 * 于是「50 个 emoji」这种确实只有 50 个字符、库也存得下的输入会被误拒。
 * <p>刻意<b>不</b>做到字素簇（grapheme cluster）级别：MySQL 也不那么算，
 * 比数据库更严只会制造「前端能过、库里放得下、却被服务端拒了」的矛盾。
 */
public final class TextRules {

    /** 统一文本规则拒绝的字符（{@code §9.2.5}、{@code NFR-20}、{@code C-15}）。 */
    private static final char ANGLE_OPEN = '<';
    private static final char ANGLE_CLOSE = '>';

    private TextRules() {
    }

    /**
     * 字符数（按码点计）。
     *
     * @param value 非 {@code null}
     * @return 码点个数
     */
    public static int charCount(String value) {
        return value.codePointCount(0, value.length());
    }

    /**
     * 是否含 {@code <} 或 {@code >}。
     *
     * <p>统一文本规则是「<b>完全拒绝，不做转义放行</b>」——注意这<b>不能</b>替代输出侧转义：
     * 输入拒绝是第一层、输出转义是第二层，<b>不可互补</b>（{@code NFR-20} ②）。
     * 例如 {@code "&lt;script&gt;"} 里一个裸尖括号都没有，本判定会放行它，
     * 所以渲染时仍然必须转义。
     */
    public static boolean containsAngleBracket(String value) {
        return value.indexOf(ANGLE_OPEN) >= 0 || value.indexOf(ANGLE_CLOSE) >= 0;
    }

    /** 是否含换行。{@code \r} 一并算，避免 {@code \r\n} 漏网。 */
    public static boolean containsLineBreak(String value) {
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }

    /**
     * 首尾 trim；结果为空串时返回 {@code null}。
     *
     * <p>用于「可选字段」的把空值收敛为 {@code null}（如 {@code goods.description}）：
     * 避免库里同时存在 {@code ''} 与 {@code NULL} 两种「空」，让下游只需处理一种。
     *
     * <p>⚠️ 「首尾空格」的口径是 <b>trim 而非拒绝</b>：§3.6 的统一文本规则写的是
     * 「<b>禁止</b>：<b>入库前 trim</b>」——冒号后给的是机制；
     * 第 10 章 {@code M10-29} 的<b>触发条件</b>栏也写的是「名称超长或含非法字符」，
     * <b>没有</b>「首尾留空格」。故粘贴带来的首尾空格被静默去掉，而不是把人挡在门外。
     */
    public static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
