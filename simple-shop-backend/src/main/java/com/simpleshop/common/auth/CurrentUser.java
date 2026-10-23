package com.simpleshop.common.auth;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;

/**
 * 从当前请求读取已认证主体。
 *
 * <p>拿不到主体时按会话失效处理，避免调用方把 {@code null} 继续当账号使用。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthPrincipal require(HttpServletRequest request) {
        return optional(request).orElseThrow(() -> new BusinessException(ErrorCode.SESSION_INVALID));
    }

    public static Optional<AuthPrincipal> optional(HttpServletRequest request) {
        Object value = request.getAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE);
        return value instanceof AuthPrincipal principal ? Optional.of(principal) : Optional.empty();
    }
}
