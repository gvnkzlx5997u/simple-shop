package com.simpleshop.common.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;

/**
 * 角色感知的鉴权拦截器：校验 {@code Authorization: Bearer <token>}。
 *
 * <p>校验通过后把主体写入请求属性，供 {@link CurrentUser} 读取。
 * 失败时抛出业务异常，由全局异常处理器输出 401 与统一响应体。
 * 日志不记录令牌内容。
 */
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    public static final String PRINCIPAL_ATTRIBUTE = "com.simpleshop.auth.principal";

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthRole role;
    private final SessionStore sessionStore;

    public AuthInterceptor(AuthRole role, SessionStore sessionStore) {
        this.role = role;
        this.sessionStore = sessionStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = extractToken(request);
        if (token == null) {
            log.warn("{} api called without bearer token: method={} path={}",
                    role, request.getMethod(), request.getRequestURI());
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }
        return sessionStore.validate(token, role)
                .map(principal -> {
                    request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
                    return true;
                })
                .orElseThrow(() -> {
                    log.warn("{} api called with invalid or expired token: method={} path={}",
                            role, request.getMethod(), request.getRequestURI());
                    return new BusinessException(ErrorCode.SESSION_INVALID);
                });
    }

    public static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.length() <= BEARER_PREFIX.length()) {
            return null;
        }
        if (!header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
