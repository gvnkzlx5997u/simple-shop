package com.simpleshop.common.api;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。编号按域划分：1xxxx 账号与会话，2xxxx 类别与商品，5xxxx 通用。
 *
 * <p>业务拒绝默认返回 HTTP 200 + 业务码；只有会话失效、参数错误、资源不存在和服务端错误
 * 使用对应的 HTTP 状态。新增错误码必须落在所属域内。
 *
 * <p>3xxxx（意向与交易）与 4xxxx（历史与归档）已随机制下线整段停用，码位不得复用：
 * 旧前端或旧日志里出现过这些编号，复用会让它们被误读成新语义。
 */
public enum ErrorCode {

    OK(0, "ok", HttpStatus.OK),

    // 1xxxx 账号与会话
    LOGIN_FAILED(10001, "invalid username or password", HttpStatus.OK),
    SESSION_INVALID(10002, "session invalid or expired", HttpStatus.UNAUTHORIZED),
    OLD_PASSWORD_MISMATCH(10003, "old password incorrect", HttpStatus.OK),
    PASSWORD_INVALID(10004, "password does not meet the requirements", HttpStatus.OK),
    USERNAME_TAKEN(10005, "username already taken", HttpStatus.OK),
    USERNAME_INVALID(10006, "username is invalid", HttpStatus.OK),
    ACCOUNT_INVALID(10010, "account is invalid", HttpStatus.OK),
    PHONE_INVALID(10007, "phone number is invalid", HttpStatus.OK),
    DEFAULT_LOCATION_INVALID(10008, "default trade location is invalid", HttpStatus.OK),
    ACCOUNT_NOT_FOUND(10009, "account not found", HttpStatus.NOT_FOUND),

    // 2xxxx 类别与商品
    CATEGORY_NOT_FOUND(20001, "category not found", HttpStatus.NOT_FOUND),
    CATEGORY_NOT_LEAF(20002, "category must be a second-level category", HttpStatus.OK),
    CATEGORY_NAME_INVALID(20003, "category name is invalid", HttpStatus.OK),
    CATEGORY_NAME_DUPLICATED(20004, "category name already exists under the same parent", HttpStatus.OK),
    CATEGORY_HAS_CHILDREN(20005, "category still has child categories", HttpStatus.OK),
    CATEGORY_IN_USE(20006, "category is still referenced by goods", HttpStatus.OK),
    GOODS_NOT_FOUND(20011, "goods not found", HttpStatus.NOT_FOUND),
    GOODS_NAME_INVALID(20012, "goods name is invalid", HttpStatus.OK),
    GOODS_DESCRIPTION_INVALID(20013, "goods description is invalid", HttpStatus.OK),
    PRICE_INVALID(20014, "price is invalid", HttpStatus.OK),
    STOCK_INVALID(20015, "stock is invalid", HttpStatus.OK),
    IMAGE_INVALID(20016, "image must be JPG or PNG and not larger than 5MB", HttpStatus.OK),
    IMAGE_LIMIT_EXCEEDED(20017, "too many images", HttpStatus.OK),
    MAIN_IMAGE_REQUIRED(20018, "a main image is required before adding gallery images", HttpStatus.OK),
    GOODS_NOT_ON_SALE(20019, "goods is not on sale", HttpStatus.OK),
    GOODS_STATUS_MISMATCH(20023, "goods status does not allow this operation", HttpStatus.OK),
    VIDEO_INVALID(20025, "video is invalid or too large", HttpStatus.OK),
    MEDIA_INVALID(20026, "media is invalid or too large", HttpStatus.OK),
    MEDIA_TYPE_NOT_ALLOWED(20027, "media type is not allowed for this purpose", HttpStatus.OK),

    // 5xxxx 通用
    INTERNAL_ERROR(50000, "internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    TOO_MANY_REQUESTS(50001, "too many requests", HttpStatus.OK),
    PARAM_INVALID(50002, "missing or malformed parameter", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(50003, "resource not found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(50004, "method not allowed", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(50005, "unsupported media type", HttpStatus.UNSUPPORTED_MEDIA_TYPE);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
