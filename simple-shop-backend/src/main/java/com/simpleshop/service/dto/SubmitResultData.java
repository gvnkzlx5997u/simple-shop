package com.simpleshop.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 提交意向成功的返回：{@code {token, intention_id}}（{@code FR-14}，方案 §4.6.2 的 {@code SubmitResultData}）。
 *
 * <h2>⚠️ {@code token} 在这里是【口令码】，不是会话令牌</h2>
 * <p>与 {@link LoginData} 的 {@code token}（卖家端 Bearer 凭据）<b>同名而完全不同</b>：
 * 本字段是买家凭以查询意向状态的 12 位口令码（{@code C-16}、{@code BR-25}）。
 * 两处都叫 {@code token} 是上游文档的既有命名，故在类注释里显式区分，避免混用。
 *
 * <h2>⚠️ 口令码只在这里「亮相」一次</h2>
 * <p>成功页（{@code P10-03}）必须显示它并提醒保存（{@code M10-04}、{@code UX-04}、{@code AC-09}），
 * 且<b>丢失后无法自助找回</b>（{@code DEC-06}：忘记密码走数据库层重置；
 * 买家侧的口令码则只能靠本人保存）。
 * <p>这也解释了第 11 章的端点约束「提交成功后<b>不得通过 URL 直接访问成功页</b>
 * （口令码仅展示一次，{@code K5-02}）——{@code B11-03} 成功后应使用<b>服务端转发</b>（forward）
 * 而非重定向」。<b>该约束属下一轮 JSP 的落地事项</b>，本轮只在 Service 侧保证
 * 「口令码随成功结果返回」这一条。
 *
 * <h2>⚠️ 日志边界</h2>
 * <p>本记录<b>不覆写 {@code toString()}</b>，而 record 的默认实现会打印<b>全部字段</b>——
 * 也就是说 <b>{@code toString()} 会把口令码打出来</b>。因此：
 * <ul>
 *   <li>本类<b>不得</b>被 {@code log.info("{}", result)} 之类的语句直接打印
 *       （{@code 12-H}：口令码绝不出现在任何日志中）；</li>
 *   <li>需要排查时只打印 {@code intentionId}，永不打印 {@code token}。</li>
 * </ul>
 * <p>之所以<b>不</b>覆写 {@code toString()} 去遮挡它：买家成功页确实需要把口令码渲染出来，
 * 遮挡字段会让 JSP 侧无法取值；而「不打印」这件事由调用方与代码评审保证，
 * 此处用注释显式登记（{@code 12-H} 的落实点是「不打印」，不是「值不存在」）。
 *
 * @param token       口令码（12 位 {@code [A-Z0-9]}，已归一化为大写）
 * @param intentionId 新意向的 ID（前缀 {@code I}）
 */
public record SubmitResultData(
        @JsonProperty("token") String token,
        @JsonProperty("intention_id") String intentionId) {

    /** 由意向信息构造。 */
    public static SubmitResultData of(String token, String intentionId) {
        return new SubmitResultData(token, intentionId);
    }
}
