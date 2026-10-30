package com.simpleshop.common.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 统一响应外壳：{@code {code, message, data}}；参数校验失败时额外带 {@code errors}。
 *
 * <p>{@code data} 无值时输出 {@code null} 且保留键，因此不得配置 NON_NULL 序列化策略。
 * {@code errors} 只在校验失败时出现，成功响应与其他业务错误不带该键。
 * {@code message} 仅供日志排查，界面文案由前端按业务码映射。
 */
public class ApiResponse<T> {

    @JsonProperty("code")
    private int code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private T data;

    @JsonProperty("errors")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<FieldErrorData> errors;

    public ApiResponse() {
    }

    private ApiResponse(int code, String message, T data, List<FieldErrorData> errors) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.errors = errors;
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(ErrorCode.OK.code(), ErrorCode.OK.message(), null, null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK.code(), ErrorCode.OK.message(), data, null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.code(), errorCode.message(), null, null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String messageOverride) {
        return new ApiResponse<>(errorCode.code(), messageOverride, null, null);
    }

    /** 参数校验失败：HTTP 400 + 50002，并附字段级说明。 */
    public static ApiResponse<Void> invalid(List<FieldErrorData> errors) {
        return new ApiResponse<>(
                ErrorCode.PARAM_INVALID.code(),
                ErrorCode.PARAM_INVALID.message(),
                null,
                List.copyOf(errors));
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

    public List<FieldErrorData> getErrors() {
        return errors;
    }

    public void setErrors(List<FieldErrorData> errors) {
        this.errors = errors;
    }
}
