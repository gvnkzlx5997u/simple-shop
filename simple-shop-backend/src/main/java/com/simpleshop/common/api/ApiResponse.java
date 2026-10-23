package com.simpleshop.common.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 统一响应外壳：{@code {code, message, data}}。
 *
 * <p>{@code data} 无值时输出 {@code null} 且保留键，因此不得配置 NON_NULL 序列化策略。
 * {@code message} 仅供日志排查，界面文案由前端按业务码映射。
 */
public class ApiResponse<T> {

    @JsonProperty("code")
    private int code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private T data;

    public ApiResponse() {
    }

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(ErrorCode.OK.code(), ErrorCode.OK.message(), null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK.code(), ErrorCode.OK.message(), data);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.code(), errorCode.message(), null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String messageOverride) {
        return new ApiResponse<>(errorCode.code(), messageOverride, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
