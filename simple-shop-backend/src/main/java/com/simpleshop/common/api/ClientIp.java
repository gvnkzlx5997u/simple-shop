package com.simpleshop.common.api;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 取客户端地址，用于限流 key。
 *
 * <p>部署在反向代理后时优先读 X-Forwarded-For / X-Real-IP；直连时退回 remoteAddr。
 * 该值只用于限流，不作为安全身份依据。
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
