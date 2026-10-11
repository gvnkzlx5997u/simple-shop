package com.simpleshop.service.support;

import java.util.regex.Pattern;

import com.simpleshop.service.dto.BuyerPrompt;
import com.simpleshop.service.dto.BuyerResult;

/**
 * 买家端字段校验：{@code intentions.name} 与 {@code intentions.tel}（方案 §3.6 的逐字段校验表）。
 *
 * <h2>规则来源</h2>
 * <table border="1">
 *   <caption>§3.6 的逐字段校验表（买家侧两行）</caption>
 *   <tr><th>字段</th><th>必填</th><th>长度</th><th>格式</th><th>文案</th></tr>
 *   <tr><td>{@code intentions.name}</td><td>是</td><td>≤50 字符</td>
 *       <td>统一文本规则 + 禁首尾空格 + <b>不允许换行</b></td><td>{@code M10-36}</td></tr>
 *   <tr><td>{@code intentions.tel}</td><td>是</td><td>7~20 位</td>
 *       <td><b>{@code ^[0-9-]{7,20}$}</b>（数字与 {@code -}，兼容座机，已决 {@code 9-L}）</td>
 *       <td>{@code M10-37}</td></tr>
 * </table>
 *
 * <h2>⚠️ 返回 {@link BuyerResult} 而不是抛异常（与 {@code GoodsValidator} 的区别）</h2>
 * <p>{@code GoodsValidator} 抛 {@code BusinessException}，因为卖家端有
 * {@code GlobalExceptionHandler} 把异常统一翻译成 HTTP 响应；
 * 买家端<b>没有</b>那一层（§11.6.2：服务端渲染、不走业务码），
 * 故这里直接返回「通过（含归一化值）或被拒（含提示码）」，见 {@link BuyerResult} 的类注释。
 *
 * <h2>⚠️ 本类不做「必填性」判断之外的业务判断</h2>
 * <p>它只回答「这两个字段本身合法吗」，不碰商品状态、队列容量、口令码——那些是 Service 的职责。
 */
public final class IntentionValidator {

    /** 姓名长度上限（{@code M10-36}、{@code varchar(50)}）。 */
    public static final int NAME_MAX_CHARS = 50;

    /**
     * 电话格式：7~20 位的数字与连字符（{@code 9-L} 已决，兼容座机如 {@code 010-12345678}）。
     *
     * <p>注意<b>不</b>要求「必须以数字开头/结尾」，也不禁止连续连字符——
     * 契约给的就是这个字符集与长度区间，多判一条就是发明约束。
     */
    private static final Pattern TEL_PATTERN = Pattern.compile("^[0-9-]{7,20}$");

    private IntentionValidator() {
    }

    /**
     * 校验并归一化买家姓名。
     *
     * @param raw 原始输入
     * @return 通过时为 {@code Ok(已 trim 的姓名)}；被拒时为
     *         {@link BuyerPrompt#NAME_TEL_REQUIRED}（缺失／全空白）
     *         或 {@link BuyerPrompt#NAME_INVALID}（超长／含 {@code <>}／含换行）
     */
    public static BuyerResult<String> normalizeName(String raw) {
        if (raw == null || raw.isBlank()) {
            return BuyerResult.rejected(BuyerPrompt.NAME_TEL_REQUIRED);
        }
        // ⚠️ 先 trim 再判长度：否则「48 字符 + 3 个空格」这种 trim 后合法的输入会被误拒
        String value = raw.trim();
        if (TextRules.charCount(value) > NAME_MAX_CHARS
                || TextRules.containsAngleBracket(value)
                || TextRules.containsLineBreak(value)) {
            return BuyerResult.rejected(BuyerPrompt.NAME_INVALID);
        }
        return BuyerResult.ok(value);
    }

    /**
     * 校验并归一化联系电话。
     *
     * @param raw 原始输入
     * @return 通过时为 {@code Ok(已 trim 的电话)}；被拒时为
     *         {@link BuyerPrompt#NAME_TEL_REQUIRED}（缺失／全空白）
     *         或 {@link BuyerPrompt#TEL_INVALID}（不匹配 {@code ^[0-9-]{7,20}$}）
     */
    public static BuyerResult<String> normalizeTel(String raw) {
        if (raw == null || raw.isBlank()) {
            return BuyerResult.rejected(BuyerPrompt.NAME_TEL_REQUIRED);
        }
        String value = raw.trim();
        if (!TEL_PATTERN.matcher(value).matches()) {
            return BuyerResult.rejected(BuyerPrompt.TEL_INVALID);
        }
        return BuyerResult.ok(value);
    }
}
