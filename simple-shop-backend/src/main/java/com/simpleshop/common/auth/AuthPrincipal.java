package com.simpleshop.common.auth;

import java.util.Objects;

/**
 * 已认证主体。
 *
 * @param role      角色
 * @param subjectId 账号主键
 * @param username  用户名，用于日志与展示
 */
public record AuthPrincipal(AuthRole role, String subjectId, String username) {

    public AuthPrincipal {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(username, "username");
    }
}
