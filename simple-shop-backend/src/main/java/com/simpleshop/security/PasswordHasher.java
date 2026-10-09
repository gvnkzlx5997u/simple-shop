package com.simpleshop.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码哈希的<b>唯一</b>入口（设计说明书 §9.8.1、{@code C-17}、{@code DEC-06}）。
 *
 * <h2>为什么单独一个类</h2>
 * <ul>
 *   <li>库内 {@code simpleshop_users.password} <b>必须</b>是不可逆哈希，<b>禁止明文</b>。
 *       把「算法 + 强度」收在一个类里，避免各处自行 {@code new BCryptPasswordEncoder(不同强度)}，
 *       导致同一列混入不同强度的哈希。</li>
 *   <li>只依赖 {@code spring-security-crypto}，<b>未</b>引入 {@code spring-boot-starter-security}，
 *       故不会带来 web 安全自动配置（本阶段无 web 层，§1.2）。</li>
 * </ul>
 *
 * <h2>BCrypt 与列宽的相容性</h2>
 * <p>BCrypt 编码恒为 <b>60 字符</b>（形如 {@code $2a$10$...}，含算法标识、强度与盐），
 * 与列宽 {@code varchar(100)} 相容（§9.2 已核算）。盐由 BCrypt 自带，
 * <b>不需要</b>额外加盐列——字典也没有该列。
 *
 * <p><b>⚠️ 不要用本类做「改密」以外的用途</b>：验证口令用 {@link #matches(String, String)}，
 * 它是<b>恒定时间</b>比较；不要用 {@code equals} 比较哈希字符串。
 */
public final class PasswordHasher {

    /**
     * BCrypt 强度（log rounds）。10 是兼顾安全与耗时的常用取值：
     * 单次校验约数十毫秒，可有效抵御离线暴力破解。
     */
    public static final int STRENGTH = 10;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(STRENGTH);

    private PasswordHasher() {
    }

    /**
     * 生成口令的 BCrypt 哈希，用于写入 {@code simpleshop_users.password}。
     *
     * @param rawPassword 明文口令（<b>不得</b>写入脚本、日志或版本库）
     * @return 60 字符的 BCrypt 编码
     */
    public static String hash(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验明文口令与库中哈希是否匹配（登录用）。
     *
     * @param rawPassword  用户输入
     * @param passwordHash 库中取出的哈希
     * @return 是否匹配；{@code passwordHash} 非 BCrypt 格式时返回 {@code false}（不抛异常）
     */
    public static boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        try {
            return ENCODER.matches(rawPassword, passwordHash);
        } catch (IllegalArgumentException e) {
            // 库中被写入了非 BCrypt 格式的值（例如历史遗留明文）
            return false;
        }
    }

    /** 判断一个字符串是否为 BCrypt 编码格式（用于巡检「库中是否混入明文」）。 */
    public static boolean isBcryptHash(String value) {
        return value != null && value.matches("^\\$2[aby]?\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    }
}
