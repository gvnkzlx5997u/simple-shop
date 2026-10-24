package com.simpleshop.common.api;

/**
 * 业务规则拒绝。由全局异常处理器翻译为对应的 HTTP 状态与业务码。
 *
 * <p>消息只写「为什么被拒」，不得包含密码、令牌等敏感值。
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String messageOverride) {
        super(messageOverride);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
