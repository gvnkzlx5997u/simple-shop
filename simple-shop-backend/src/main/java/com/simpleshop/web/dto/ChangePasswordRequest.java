package com.simpleshop.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code I11-03}（修改密码）的请求体：{@code {old_password, new_password}}。
 *
 * <p>依据：第 11 章 §11.6.1、方案 §4.6.3、{@code C-17}（新密码长度 ≥ 8）、
 * {@code FR-02}、{@code DEC-17}（改密后全部会话失效）。
 *
 * <h2>⚠️ 为什么 {@code new_password} 上【没有】{@code @Size(min = 8)}（对方案 §4.6.3 的修正）</h2>
 * <p>方案 §4.6.3 的请求 DTO 表原写作 {@code new_password} 加 {@code @Size(min = 8)}。
 * 这与契约<b>冲突</b>，实现时已修正，原因是一条硬规则：
 *
 * <blockquote>
 * <b>DTO 注解只能拦「报文本身不合法」，不得拦「已有专用业务错误码的取值规则」。</b>
 * </blockquote>
 *
 * <p>第 11 章 §11.6.1 为「新密码长度不足 8 位」专门分配了错误码 {@code 10004}，
 * 且它的 HTTP 状态是 <b>200</b>（方案 §4.3：业务规则拒绝属正常分支）。若这里加了
 * {@code @Size(min = 8)}，请求会在进入 Controller 之前就被 Bean Validation 拦下，
 * 经 {@code GlobalExceptionHandler} 变成 {@code 50002} + <b>HTTP 400</b>：
 * <ul>
 *   <li>契约指定的 {@code 10004} 在 HTTP 路径上<b>永远不可达</b>（成为死码）；</li>
 *   <li>前端按 {@code code} 映射文案（{@code UX-07}、{@code NFR-09}）时，
 *       拿到的是 {@code 50002} 的「参数错误」，而<b>不是</b> {@code 10004} 的
 *       「新密码至少 8 位」就地提示——界面文案就会错。</li>
 * </ul>
 * <p>因此长度下限于 <b>Service</b> 判定并抛 {@code 10004}（见 {@code SellerAuthService}），
 * 这里只保留 {@code @NotBlank}——「字段缺失／空白」属于「报文不合法」，
 * 走 {@code 50002}（HTTP 400）是正确的。
 *
 * <p><b>⚠️ 这条规则是系统性的</b>，不止 {@code 10004}。方案 §4.6.3 里凡是
 * 「DTO 上写了上限／下限注解」<b>且</b>该字段另有专属业务码的，都属同一类冲突：
 * {@code goods.name} 的 {@code @Size(max=50)} 与 {@code 20007}、
 * {@code description} 的 {@code @Size(max=500)} 与 {@code 20008}、
 * {@code fail_reason} 的 {@code @Size(max=300)} 与 {@code 30007}——
 * 这些须在 S4／S6 按同一规则处理：<b>注解只留 {@code @NotBlank}（判有无），
 * 长度与字符规则交给 Service（判对不对）</b>。
 * <p>（反例：{@code page}／{@code page_size} 的 {@code @Min}/{@code @Max} <b>保留</b>——
 * 因为契约没有为它们分配专属业务码，{@code 50002} 就是指定码，不存在冲突。）
 *
 * <h2>为什么没有 {@code @Size(min = 8)} 也不会「漏校验」</h2>
 * <p>因为 Service 侧仍独立校验（方案 §3.6 的 {@code G6-02} 要求，
 * 且买家端 JSP 下一轮直接调 Service 时不过 DTO 校验链）。
 * 两处都写才是重复的双源；<b>只有 Service 一处</b>是唯一正确的落点。
 */
public class ChangePasswordRequest {

    /** 原密码。空值 → {@code 50002}；不匹配 → {@code 10003}（由 Service 判定）。 */
    @NotBlank(message = "old_password is required")
    @JsonProperty("old_password")
    private String oldPassword;

    /**
     * 新密码。
     *
     * <p>只有 {@code @NotBlank}：长度下限（≥ 8）由 Service 判定并返回 {@code 10004}，
     * 理由见类注释。
     */
    @NotBlank(message = "new_password is required")
    @JsonProperty("new_password")
    private String newPassword;

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    /**
     * ⚠️ <b>新旧密码一律不输出</b>。
     *
     * <p>比 {@code LoginRequest} 更要紧：这里的两个字段都是<b>明文口令</b>，
     * 且「修改密码」本身就是 {@code NFR-12} 要求记日志的 8 类操作之一——
     * 操作日志与调试日志往往是同一个人在看，一个不小心就会把口令写进审计文件。
     */
    @Override
    public String toString() {
        return "ChangePasswordRequest{old_password=***, new_password=***}";
    }
}
