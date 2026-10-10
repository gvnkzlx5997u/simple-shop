package com.simpleshop.web;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.simpleshop.service.exception.ErrorCode;

/**
 * 统一响应结构（第 11 章 §11.3.3）。
 *
 * <pre>
 * 成功：      { "code": 0, "message": "ok", "data": { } }
 * 业务失败：  { "code": 20006, "message": "intention queue is not empty", "data": null }
 * </pre>
 *
 * <h2>⚠️ 三个必须守住的点</h2>
 * <ol>
 *   <li><b>字段名是 JSON 契约的一部分</b>：{@code code} / {@code message} / {@code data}。
 *       用 {@code @JsonProperty} <b>显式</b>声明，不依赖全局命名策略（见 {@code JacksonConfig} 的类注释）。</li>
 *   <li><b>{@code data} 无值时必须是 {@code null} 且<b>键必须存在</b></b>
 *       （{@code 11.3.1}「可选字段无值时返回 null，<b>不省略键</b>」）。
 *       因此<b>不得</b>在本类或全局配置 {@code @JsonInclude(NON_NULL)}——
 *       它会把 {@code "data":null} 整个删掉。{@code AppPropertiesTest.keepsNullFields} 是这条的回归护栏。</li>
 *   <li><b>{@code message} 不得展示给用户</b>（{@code 11.3.3}、{@code UX-07}）：它仅供日志与排查，
 *       前端须按 {@code code} 映射第 10 章《提示文案表》的 {@code M10-xx}。
 *       本类用 ASCII 英文填充它（已定 Q-13）。</li>
 * </ol>
 *
 * <h2>为什么用静态工厂而不是构造器</h2>
 * <p>调用处一律写成 {@code ApiResponse.ok(...)} / {@code ApiResponse.error(code)}，
 * 于眼能看出「这是成功还是失败」，也避免 {@code new ApiResponse<>(0, "ok", x)} 这种
 * 把「成功码是多少」散落到各处的写法——成功码只有 {@link ErrorCode#OK} 一个来源。
 *
 * @param <T> 业务数据类型；无数据时为 {@code Void}（{@code data} 序列化为 {@code null}）
 */
public class ApiResponse<T> {

    @JsonProperty("code")
    private int code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private T data;

    /** 供 Jackson 反序列化与测试断言使用；业务代码请用静态工厂。 */
    public ApiResponse() {
    }

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // -------------------------------------------------------------------------
    // 成功
    // -------------------------------------------------------------------------

    /** 成功且无数据（{@code data} 为 {@code null}）：契约里出参为 {@code null} 的那些接口用它。 */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(ErrorCode.OK.code(), ErrorCode.OK.message(), null);
    }

    /** 成功并携带数据。 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK.code(), ErrorCode.OK.message(), data);
    }

    // -------------------------------------------------------------------------
    // 失败
    // -------------------------------------------------------------------------

    /**
     * 业务失败，{@code message} 取错误码表的标准描述。
     *
     * <p>HTTP 状态码由 {@link ErrorCode#httpStatus()} 决定，<b>不在这里体现</b>——
     * 响应体的 {@code code} 与 HTTP 状态是两条轨道（{@code 11-B}、{@code 11-E}）。
     */
    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.code(), errorCode.message(), null);
    }

    /**
     * 业务失败，覆盖默认描述。
     *
     * <p>⚠️ 覆盖内容<b>不得包含</b>口令码、密码等敏感值（见 {@code BusinessException} 类注释）。
     */
    public static ApiResponse<Void> error(ErrorCode errorCode, String messageOverride) {
        return new ApiResponse<>(errorCode.code(), messageOverride, null);
    }

    // -------------------------------------------------------------------------
    // 访问器（供测试与 Jackson 使用）
    // -------------------------------------------------------------------------

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setData(T data) {
        this.data = data;
    }
}
