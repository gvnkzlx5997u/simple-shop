package com.simpleshop.service.support;

import com.simpleshop.persistence.enums.FailType;
import com.simpleshop.service.exception.BusinessException;
import com.simpleshop.service.exception.ErrorCode;

/**
 * 「标记交易失败」两个输入字段的校验（方案 §3.6 的逐字段校验表）。
 *
 * <table border="1">
 *   <caption>{@code I11-13} 的两个字段</caption>
 *   <tr><th>字段</th><th>必填</th><th>规则</th><th>违规码</th></tr>
 *   <tr><td>{@code disposal}</td><td>是</td><td>枚举 {@code voided}／{@code requeued}</td><td>{@code 30006}</td></tr>
 *   <tr><td>{@code fail_reason}</td><td>否</td>
 *       <td>≤300 字符；统一文本规则（拒 {@code <}/{@code >}）；<b>允许换行</b>；首尾 trim</td>
 *       <td>{@code 30007}</td></tr>
 * </table>
 *
 * <h2>⚠️ {@code disposal} 为什么按 {@code String} 收、在 Service 里判（已定 Q-14）</h2>
 * <p>若把请求 DTO 的 {@code disposal} 声明成 Java 枚举，非法值会在 <b>Jackson 反序列化阶段</b>
 * 就抛 {@code HttpMessageNotReadableException}，响应变成 {@code 50002} + HTTP <b>400</b>——
 * 而契约给这个字段分配的是 <b>{@code 30006}（HTTP 200）</b>，于是又出现一条「专属业务码在
 * HTTP 路径上永不可达」的死码（与 S3 发现的 {@code 10004}／{@code 20007} 同类，
 * 硬规则见方案 §4.6.3）。更糟的是，想在这种写法下保住 {@code 30006}，就得在
 * {@code GlobalExceptionHandler} 里靠<b>解析异常的消息文本</b>去猜是哪个字段出错——脆弱且归属不准。
 *
 * <h2>⚠️ 换行：{@code fail_reason} <b>允许</b></h2>
 * <p>与商品名称／买家姓名相反（那两个字段不允许换行）。失败备注是自由文本，
 * 卖家可能分几行写清原因，拒绝换行会是无理由的约束。
 * <p>「允许换行」只意味着<b>不因换行而拒绝</b>；{@code <}/{@code >} 仍然照拒
 * （{@code NFR-20} 的 XSS 第一层，见 {@link TextRules}）。
 *
 * <h2>⚠️ 本类的返回值：{@code disposal} 归一化为 {@link FailType}</h2>
 * <p>不另造一个 {@code TradeDisposal} 枚举：{@link FailType} 已经有 {@code voided}／{@code requeued}
 * 两个常量，且它们<b>正是</b>要写进 {@code intentions.fail_type} 的值。
 * 若再造一个平行枚举，就得维护一张「两个枚举常量一一对应」的映射表——
 * 多一处会静默写错的地方，收益为零。{@link FailType} 里另外两个常量（{@code sold_out}／
 * {@code revoked}）<b>不是</b>合法裁决值，故本类的白名单只有那两个。
 */
public final class TradeFailureValidator {

    /** 失败备注长度上限（{@code §3.6} 逐字段表与列宽 {@code varchar(300)} 一致）。 */
    public static final int FAIL_REASON_MAX_CHARS = 300;

    /** 合法裁决值之一：作废。 */
    private static final String VOIDED = "voided";

    /** 合法裁决值之一：重新排队。 */
    private static final String REQUEUED = "requeued";

    private TradeFailureValidator() {
    }

    /**
     * 解析并校验裁决方式。
     *
     * <p>⚠️ {@code null}／空串／未知值<b>一律</b>归 {@code 30006}，不走 {@code 50002}：
     * §4.6.3 的逐字段表明确 {@code disposal} 「不加（按 {@code String} 收，Q-14）」注解，
     * 由 Service 判值；而「取值非法」正包含「给了一个不在枚举里的值」。
     *
     * @param raw 原始取值（区分大小写：契约给的是小写代码）
     * @return {@link FailType#voided} 或 {@link FailType#requeued}
     * @throws BusinessException {@code 30006}——取值非法
     */
    public static FailType parseDisposal(String raw) {
        if (VOIDED.equals(raw)) {
            return FailType.voided;
        }
        if (REQUEUED.equals(raw)) {
            return FailType.requeued;
        }
        throw new BusinessException(ErrorCode.INVALID_DISPOSAL);
    }

    /**
     * 校验并归一化失败备注。
     *
     * @param raw 原始输入，可为 {@code null}
     * @return 归一化后的备注；未提供或 trim 后为空时返回 {@code null}
     * @throws BusinessException {@code 30007}——超长或含 {@code <}／{@code >}
     */
    public static String normalizeFailReason(String raw) {
        // 空备注与「没有备注」等价，统一收敛为 null（与商品描述同一口径，
        // 避免库里同时存在 '' 与 NULL 两种「空」）。
        String value = TextRules.trimToNull(raw);
        if (value == null) {
            return null;
        }
        if (TextRules.charCount(value) > FAIL_REASON_MAX_CHARS
                || TextRules.containsAngleBracket(value)) {
            throw new BusinessException(ErrorCode.FAIL_REASON_TOO_LONG);
        }
        return value;
    }
}
