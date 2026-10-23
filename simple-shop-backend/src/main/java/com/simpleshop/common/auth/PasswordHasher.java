package com.simpleshop.common.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码哈希的唯一入口：卖家账号与买家账号共用。
 *
 * <p>使用 BCrypt（强度 10），输出固定 60 字符。校验必须走 {@link #matches}，
 * 不要用字符串相等比较哈希。
 */
public final class PasswordHasher {

    public static final int STRENGTH = 10;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(STRENGTH);

    private PasswordHasher() {
    }

    public static String hash(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        try {
            return ENCODER.matches(rawPassword, passwordHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static boolean isBcryptHash(String value) {
        return value != null && value.matches("^\\$2[aby]?\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    }
}
