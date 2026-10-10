package com.simpleshop.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code I11-01}（登录）的请求体：{@code {account, password}}。
 *
 * <p>依据：第 11 章 §11.6.1、方案 §4.6.3。
 *
 * <h2>为什么是普通类而不是 {@code record}</h2>
 * <p>请求 DTO 需要「无参构造 + setter」供 Spring MVC 做数据绑定；且 Bean Validation 注解写在
 * <b>字段</b>上最直观。响应 DTO（{@code service/dto/*Data}）则用 {@code record}
 * 换取编译期的字段集合保证——两边形态不同是有意的，理由见 {@code LoginData} 的类注释。
 *
 * <h2>⚠️ 校验注解只拦「报文本身不合法」，不拦业务规则</h2>
 * <p>本类的注解只负责把「缺字段／超长」这类问题变成 {@code 50002}（HTTP 400）。
 * <b>业务规则仍由 Service 独立校验</b>（{@code G6-02}、方案 §3.6），因为：
 * <ul>
 *   <li>Service 也被买家端 JSP（下一轮）直接调用，届时<b>不经过</b> DTO 校验链；</li>
 *   <li>「账号或密码错误」（{@code 10001}）必须在 Service 判定，
 *       且<b>账号不存在与密码错误必须返回同一个码</b>（{@code M10-23}）。</li>
 * </ul>
 *
 * <h2>为什么 {@code password} 只有 {@code @NotBlank}、没有长度上限</h2>
 * <p>方案 §3.6 的逐字段校验表明确：<b>「登录侧不设长度限制」</b>。
 * 对登录入参加长度限制是常见但<b>错误</b>的做法——它会让「密码长度不合规」与
 * 「账号密码错误」产生两个可区分的响应，等于给出一条免费的探测信号，
 * 与 {@code 10001}「统一提示、不区分」的口径冲突。长度约束只属于<b>设置新密码</b>时
 * （{@code I11-03}，{@code 10004}）。
 *
 * <h2>为什么 {@code account} 不做 {@code trim}</h2>
 * <p>契约（方案 §3.6）只为<b>口令码</b>规定了「归一化后校验」（大小写与首尾空格），
 * 对 {@code account} <b>没有</b>任何归一化要求。这里刻意不做隐式 {@code trim}：
 * 隐式归一化会让「看起来一样但实际不同」的输入产生不同结果，排查时无从下手。
 * 全角空格、前后空格等一律按「账号不存在」处理，走同一个 {@code 10001}。
 */
public class LoginRequest {

    /**
     * 登录账号。
     *
     * <p>{@code @Size(max = 50)} 与库列宽 {@code account varchar(50)} 一致（方案 §3.6 的
     * {@code account ≤ 50} 行）。超长属于「报文不合法」，故为 {@code 50002}（HTTP 400），
     * <b>不是</b> {@code 10001}——后者留给「格式合法但凭证不对」。
     */
    @NotBlank(message = "account is required")
    @Size(max = 50, message = "account must not exceed 50 characters")
    @JsonProperty("account")
    private String account;

    /** 登录口令。<b>不设长度限制</b>（理由见类注释）；空值属于「报文不合法」→ {@code 50002}。 */
    @NotBlank(message = "password is required")
    @JsonProperty("password")
    private String password;

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * ⚠️ <b>不输出 {@code password}</b>。
     *
     * <p>本类目前不被打印，但一旦有人为了排查而 {@code log.info("req={}", request)}，
     * 明文口令就会进日志（{@code NFR-12} 的安全面）。默认的 {@code toString} 不会有这个问题
     * ——它输出的是 {@code LoginRequest@1a2b3c}；这里之所以仍然覆写，是为了让
     * 「想知道里面有什么」的人有安全的选项，而不是被迫去打印字段。
     */
    @Override
    public String toString() {
        return "LoginRequest{account='" + account + "', password=***}";
    }
}
