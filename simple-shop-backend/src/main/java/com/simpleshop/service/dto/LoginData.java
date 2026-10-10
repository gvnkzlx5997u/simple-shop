package com.simpleshop.service.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.simpleshop.service.support.UtcIso8601;

/**
 * {@code I11-01}（登录）的响应数据：{@code {token, expire_at}}。
 *
 * <p>依据：第 11 章 §11.6.1 的 {@code I11-01} 出参、方案 §4.6.2。
 *
 * <h2>⚠️ 这里的 {@code token} 是【会话令牌】，不是【口令码】</h2>
 * <p>项目里有两个都叫「token」的东西，含义完全不同，不要混：
 * <table border="1">
 *   <caption>两种 token 的区分</caption>
 *   <tr><th></th><th>本类的 {@code token}</th><th>{@code intentions.token}（口令码）</th></tr>
 *   <tr><td>用途</td><td>卖家端接口鉴权</td><td>买家凭它查询自己的意向状态</td></tr>
 *   <tr><td>传输方式</td><td>请求头 {@code Authorization: Bearer <token>}</td><td>URL／表单里的 {@code token} 参数</td></tr>
 *   <tr><td>持有者</td><td>仅卖家（1 个账号）</td><td>每一位买家</td></tr>
 *   <tr><td>形态</td><td>32 字节随机数的 URL-safe Base64（≈43 字符）</td><td>12 位 {@code [A-Z0-9]}（{@code BR-32}）</td></tr>
 *   <tr><td>生命周期</td><td>30 分钟无操作即失效（{@code C-07}）</td><td>意向终态前一直有效（{@code INV-08}）</td></tr>
 *   <tr><td>可否进日志</td><td>否（凭据）</td><td>否（{@code 12-H}，{@code I11-14} 的日志只记意向 ID）</td></tr>
 * </table>
 *
 * <h2>为什么用 {@code record} 而不是带 setter 的类</h2>
 * <p>响应 DTO 是<b>不可变值对象</b>，其「组件清单」<b>就是</b>契约字段清单。
 * 用 {@code record} 可以在编译期保证「不会多出字段」——这与方案 §4.6.4
 * 「映射必须显式列字段，不用反射式拷贝」的意图完全一致（反射拷贝会静默把实体新增字段带进响应）。
 * <p>请求 DTO（{@code web/dto/*Request}）则相反：Spring MVC 需要无参构造 + setter 做数据绑定，
 * 故那边用普通类。两边形态不同是<b>有意</b>的，不是疏漏。
 *
 * @param token    会话令牌（Bearer 凭据）
 * @param expireAt 过期时刻，ISO 8601 UTC 带 {@code Z}
 */
public record LoginData(
        @JsonProperty("token") String token,
        @JsonProperty("expire_at") String expireAt) {

    /**
     * 由会话信息构造。
     *
     * <p>时间转换点在此（方案 §4.6.4）：调用方传入原始的 {@link Instant}，
     * 转 {@code String} 的动作<b>只</b>发生在 {@link UtcIso8601} 里，
     * 避免各处自行格式化导致格式漂移。
     *
     * @param token    会话令牌
     * @param expireAt 会话过期时刻
     */
    public static LoginData of(String token, Instant expireAt) {
        return new LoginData(token, UtcIso8601.ofInstant(expireAt));
    }
}
