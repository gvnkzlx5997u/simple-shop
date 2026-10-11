package com.simpleshop.service.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.simpleshop.service.exception.BusinessException;
import com.simpleshop.service.exception.ErrorCode;

/**
 * 商品字段校验与归一化（方案 §3.6 的「逐字段校验表」在本类的落实）。
 *
 * <h2>为什么校验放在应用层、而且必须由 Service 独立做</h2>
 * <p>数据库侧<b>没有</b>任何值校验：枚举取值、格式、区间 CHECK 全部已删除
 * （{@code DEC-DB-14} 删掉 18 条，含 {@code ck_goods_status}、{@code ck_goods_price}）——
 * 那些被删掉的约束清单<b>就是</b>本类的责任清单。数据库只管结构（长度、可空、外键、唯一）。
 * <p>DTO 上的 Bean Validation 也不能代替本类（{@code G6-02}）：
 * <ul>
 *   <li>本类被 Service 直接使用，而买家端 JSP（下一轮）会直接调 Service、<b>不过</b> DTO 校验链；</li>
 *   <li>按 S3 确立的硬规则（方案 §4.6.3），<b>DTO 注解只判「有没有」，取值规则一律归本类</b>——
 *       否则请求会在进 Controller 前被拦成 {@code 50002}，把契约指定的业务码
 *       （{@code 20007}/{@code 20008}/{@code 20010}，均为 HTTP <b>200</b>）变成死码。</li>
 * </ul>
 *
 * <h2>错误码分工（本类的核心口径）</h2>
 * <table border="1">
 *   <caption>{@code 50002} 与字段专属码的边界</caption>
 *   <tr><th>情形</th><th>码</th><th>依据</th></tr>
 *   <tr><td>字段<b>缺失／全空白</b>（「有没有」）</td><td>{@code 50002}（HTTP 400）</td>
 *       <td>{@code 11.3.4}：400 = 报文本身不合法。「必填缺失」正属此类</td></tr>
 *   <tr><td>字段<b>存在但取值非法</b>（「对不对」）</td><td>该字段专属码（HTTP 200）</td>
 *       <td>{@code 11.6.1} 为该字段分配了专属码时</td></tr>
 * </table>
 * <p>这样 Service 与 HTTP 路径对同一输入给出<b>同一个</b>码——不会出现「直接调 Service 得到
 * {@code 20007}、过一遍 Controller 却得到 {@code 50002}」这种两套口径。
 *
 * <h2>⚠️ 「首尾空格」为什么是 trim 而不是拒绝</h2>
 * <p>§3.6 的统一文本规则写的是「首尾空格｜<b>禁止</b>：<b>入库前 trim</b>；中间空格允许」——
 * 冒号后给的是<b>机制</b>：禁止的手段就是 trim，而不是报错。
 * <p>第 10 章 {@code M10-29} 也支持这个读法：它的<b>触发条件</b>栏写的是
 * 「名称<b>超长或含非法字符</b>」，<b>没有</b>「首尾留空格」；文案里的「首尾不要留空格」
 * 是给用户的建议（反正会被去掉，留着没意义）。
 * <p>反过来若做成拒绝，用户从别处粘贴 <code>" iPhone "</code> 就会莫名被拒，
 * 而库里本来只需要 <code>"iPhone"</code>。
 *
 * <h2>⚠️ 统一文本规则（拒绝 {@code <>}、码点计数、trim 口径）在 {@link TextRules}</h2>
 * <p>那几条规则同时适用于 {@code goods.name}、{@code goods.description}、
 * {@code intentions.name} 与 {@code fail_reason}（S6），因此实现收敛在 {@link TextRules} 一处——
 * 它们本质是<b>安全规则</b>（{@code NFR-20} 的第一层），不该有需要同步修改的多份副本。
 * <p>本类只负责<b>商品这两个字段</b>的必填性、长度上限、是否允许换行与价格的解析口径。
 */
public final class GoodsValidator {

    /** 名称长度上限（{@code M10-29}、{@code varchar(50)}）。 */
    public static final int NAME_MAX_CHARS = 50;

    /** 描述长度上限（{@code M10-30}、{@code varchar(500)}）。 */
    public static final int DESCRIPTION_MAX_CHARS = 500;

    /** 价格小数位数（{@code M10-32}「最多两位小数」、{@code decimal(8,2)}）。 */
    public static final int PRICE_SCALE = 2;

    /** 价格上限（{@code M10-32}、{@code decimal(8,2)} 能表达的最大值）。 */
    public static final BigDecimal PRICE_MAX = new BigDecimal("999999.99");

    private GoodsValidator() {
    }

    // -------------------------------------------------------------------------
    // 商品名称
    // -------------------------------------------------------------------------

    /**
     * 校验并归一化商品名称（{@code M10-29}）。
     *
     * <p>规则（§3.6）：必填；≤50 字符；拒绝 {@code <}／{@code >}；**不允许换行**；首尾 trim。
     *
     * @param raw 原始输入
     * @return 归一化后的名称（已 trim）
     * @throws BusinessException {@code 50002}（缺失或全空白）、{@code 20007}（超长／含 {@code <>}／含换行）
     */
    public static String normalizeName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        String value = raw.trim();
        // ⚠️ 顺序：先 trim 再判长度。若先判长度，「 48 字符 + 3 个空格」这种
        //    原本合法的输入会被误拒（trim 后只有 48 字符）。
        if (TextRules.charCount(value) > NAME_MAX_CHARS
                || TextRules.containsAngleBracket(value)
                || TextRules.containsLineBreak(value)) {
            throw new BusinessException(ErrorCode.GOODS_NAME_INVALID);
        }
        return value;
    }

    // -------------------------------------------------------------------------
    // 商品描述
    // -------------------------------------------------------------------------

    /**
     * 校验并归一化商品描述（{@code M10-30}）。
     *
     * <p>规则（§3.6）：**可选**；≤500 字符；拒绝 {@code <}／{@code >}；**允许换行**；首尾 trim。
     *
     * @param raw 原始输入，可为 {@code null}
     * @return 归一化后的描述；未提供或 trim 后为空时返回 {@code null}
     * @throws BusinessException {@code 20008}（超长或含 {@code <>}）
     */
    public static String normalizeDescription(String raw) {
        // 空描述与「没有描述」在语义上等价，统一收敛为 null（TextRules.trimToNull）：
        // 避免库里同时存在 '' 与 NULL 两种「空」，让下游（前端判断、归档、历史详情）只需处理一种。
        String value = TextRules.trimToNull(raw);
        if (value == null) {
            return null;
        }
        if (TextRules.charCount(value) > DESCRIPTION_MAX_CHARS
                || TextRules.containsAngleBracket(value)) {
            throw new BusinessException(ErrorCode.GOODS_DESCRIPTION_INVALID);
        }
        return value;
    }

    // -------------------------------------------------------------------------
    // 商品价格
    // -------------------------------------------------------------------------

    /**
     * 解析并校验价格字符串（{@code M10-32}）。
     *
     * <p>规则（§3.6）：必填；{@code 0 < price ≤ 999999.99}；**最多两位小数**；
     * 用 {@link BigDecimal}，**禁止 {@code double}**（{@code 11-F}、{@code C-12}）。
     *
     * <h2>⚠️ 错误码的细分（依 {@code M10-32} 的「触发条件」栏）</h2>
     * <ul>
     *   <li><b>缺失／空白</b> → {@code 50002}：属「必填缺失」，走报文错误（HTTP 400）。</li>
     *   <li><b>解析不出数字</b>（如 {@code "abc"}）→ {@code 50002}：{@code M10-32} 的触发条件是
     *       「价格<b>越界</b>」，而「不是数字」是<b>格式</b>问题，不在「越界」范围内，
     *       归 {@code 11.3.4} 的「参数格式错误」更准。方案 §8.3 对这几个样例要求
     *       「全部拒绝或<b>明确处理</b>」——此处即为明确处理。</li>
     *   <li><b>越界或小数位过多</b> → {@code 20010}：{@code M10-32} 的文案
     *       「价格须大于 0 且不超过 999,999.99，<b>最多两位小数</b>」三者都点名了。</li>
     * </ul>
     *
     * <h2>⚠️ 判定顺序是有意的（防一次分配放大）</h2>
     * <p>先判小数位、再判区间、**最后**才 {@code setScale}。
     * 若把 {@code setScale} 放在区间判定之前，输入 {@code "1e100000000"}
     * （{@link BigDecimal} 允许的合法科学计数法，其 {@code scale} 为负、不触发「小数位过多」）
     * 会迫使 {@code setScale(2)} 展开成上亿位十进制数——一个几十字节的请求就能吃掉大量内存与 CPU。
     * 先做 {@code compareTo} 区间判定（不展开数字）即可把它挡在 {@code 20010}。
     *
     * @param raw 原始输入（字符串，不是 {@code double}）
     * @return 归一化为 scale=2 的 {@link BigDecimal}
     * @throws BusinessException {@code 50002}（缺失／无法解析）、{@code 20010}（越界／小数位过多）
     */
    public static BigDecimal parsePrice(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        if (parsed.scale() > PRICE_SCALE) {
            // "1.999" → 直接拒绝，不做静默四舍五入（§3.6 价格处理要点）
            throw new BusinessException(ErrorCode.PRICE_OUT_OF_RANGE);
        }
        // ⚠️ 必须是 compareTo 而不是 equals：BigDecimal.equals 连 scale 一起比，
        //    new BigDecimal("0.0").equals(BigDecimal.ZERO) 为 false。
        if (parsed.compareTo(BigDecimal.ZERO) <= 0 || parsed.compareTo(PRICE_MAX) > 0) {
            throw new BusinessException(ErrorCode.PRICE_OUT_OF_RANGE);
        }
        // 走到这里说明 |值| 必然很小，setScale 不会产生大数分配
        return parsed.setScale(PRICE_SCALE, RoundingMode.UNNECESSARY);
    }
}
