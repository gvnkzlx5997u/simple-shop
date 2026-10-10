package com.simpleshop.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 查看口令码的响应数据（{@code I11-14} 的 {@code data}，形如 {@code {"token": "..."}}）。
 *
 * <p>依据：第 11 章 §11.5、方案 §4.6.2。字段就一个，但<b>仍然独立成 DTO</b>，
 * 不让 Controller 直接返回 {@code String}：{@code data} 若是一个裸字符串，
 * 响应会变成 {@code "data": "AB12..."} 而不是 {@code "data": {"token": "..."}}，
 * 前端取值路径与契约不一致。用 record 让「一个字段」也是<b>具名</b>的。
 *
 * <h2>⚠️ 本 DTO 只在<b>非终态</b>意向时才会被构造</h2>
 * <p>终态意向的口令码已失效（{@code BR-26}、{@code INV-08}），此时 Service 抛 {@code 40002}，
 * <b>不会</b>返回本对象——{@code NFR-16} 要求「不展示」。因此本类不需要
 * 「token 为空时怎么渲染」这种分支。
 *
 * <h2>⚠️ 口令码是明文返回的（已接受的风险）</h2>
 * <p>{@code FR-24}／{@code I11-14} 要求返回<b>原文</b>，意味着口令码必须可反查
 * （明文或可逆存储）。该风险已登记（{@code DEC-05}、{@code NFR-16}、{@code BR-30}）。
 *
 * <h2>⚠️ 本记录<b>不</b>覆写 {@code toString()}——与 {@link SubmitResultData} 同一口径</h2>
 * <p>record 的默认 {@code toString()} 会打印全部组件，也就是说
 * {@code log.info("{}", passcodeData)} 会把口令码明文写进日志（违反 {@code 12-H}）。
 * 本类<b>仍不</b>做遮蔽，理由与 {@code SubmitResultData} 相同：
 * <ul>
 *   <li>遮蔽 {@code toString()} 挡不住真正的泄漏路径——调用方完全可以打
 *       {@code log.info("{}", data.token())}；它只是把「不打印」这件事伪装成了「值取不到」；</li>
 *   <li>而 {@code token} 恰恰是<b>必须能取到</b>的字段（前端要展示原文），
 *       任何「让值变得难以取出」的设计都会立刻被绕过或被迫放开。</li>
 * </ul>
 * <p>因此本项目的边界是：<b>请求 DTO 里含口令/密码的字段遮蔽 {@code toString()}</b>
 * （{@code LoginRequest}／{@code ChangePasswordRequest}——它们可能被框架的日志/异常处理器打印），
 * 而<b>携带口令码的响应记录不遮蔽、但由调用方保证不打印</b>。这条边界由代码评审守护，
 * 并已在实现过程记录里登记为检查项。
 *
 * @param token 口令码原文（12 位 {@code [A-Z0-9]}）
 */
public record PasscodeData(@JsonProperty("token") String token) {
}
