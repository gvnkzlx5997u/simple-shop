package com.simpleshop.security;

import java.io.Console;

/**
 * 口令哈希命令行工具（运维／交付用）。
 *
 * <h2>用途</h2>
 * <ol>
 *   <li><b>生成</b>：为新的卖家账号口令生成 BCrypt 哈希，用于替换
 *       {@code V2__seed_initial_data.sql} 中的哈希，或直接 {@code UPDATE} 数据库。</li>
 *   <li><b>校验</b>：确认某明文口令是否与库中哈希匹配（排查登录失败）。</li>
 * </ol>
 *
 * <h2>用法</h2>
 * <pre>
 * # 生成（Java 21 单文件源码模式，通过 classpath 使用已编译的依赖）
 * java -cp "target/classes;&lt;依赖&gt;" src/main/java/com/simpleshop/security/PasswordHashTool.java gen
 *
 * # 校验
 * java -cp "target/classes;&lt;依赖&gt;" src/main/java/com/simpleshop/security/PasswordHashTool.java \
 *      verify '$2a$10$...'
 * </pre>
 *
 * <h2>⚠️ 为什么不接受命令行明文参数</h2>
 * <p>命令行参数会被<b>写进 shell 历史</b>与<b>进程列表</b>（同机其它用户可见），
 * 部分操作系统还会记录到审计日志。因此本工具在未提供参数时走<b>交互式输入且不回显</b>
 * （{@link Console#readPassword()}）。若确实需要非交互调用（如脚本），
 * 可显式传入明文作为第 2 个参数，但须自行承担上述泄漏风险。
 */
public final class PasswordHashTool {

    private PasswordHashTool() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            return;
        }
        switch (args[0]) {
            case "gen" -> gen(args.length > 1 ? args[1] : null);
            case "verify" -> verify(args);
            default -> usage();
        }
    }

    private static void gen(String rawFromArg) {
        String raw = rawFromArg != null ? rawFromArg : readSecretTwice();
        if (raw == null) {
            return;
        }
        String hash = PasswordHasher.hash(raw);
        System.out.println();
        System.out.println("BCrypt hash (strength " + PasswordHasher.STRENGTH + ", " + hash.length() + " chars):");
        System.out.println(hash);
        System.out.println();
        System.out.println("自检 matches = " + PasswordHasher.matches(raw, hash));
        System.out.println();
        System.out.println("用法一（改库，立即生效）：");
        System.out.println("  UPDATE simpleshop_users");
        System.out.println("     SET password = '" + hash + "',");
        System.out.println("         update_at = UTC_TIMESTAMP()");
        System.out.println("   WHERE account = 'seller';");
        System.out.println();
        System.out.println("用法二（改种子脚本，重建库时生效）：");
        System.out.println("  把 V2__seed_initial_data.sql 中的 password 字面量替换为上面的哈希。");
    }

    private static void verify(String[] args) {
        if (args.length < 2) {
            System.out.println("verify 需要提供库中的 BCrypt 哈希。");
            usage();
            return;
        }
        String hash = args[1];
        System.out.println("哈希格式合法 = " + PasswordHasher.isBcryptHash(hash));
        String raw = args.length > 2 ? args[2] : readSecret();
        if (raw == null) {
            return;
        }
        System.out.println("matches = " + PasswordHasher.matches(raw, hash));
    }

    /** 交互式读取口令（不回显）。 */
    private static String readSecret() {
        Console console = System.console();
        if (console == null) {
            System.err.println("当前环境没有可用的控制台（无法安全读取口令）。");
            System.err.println("请改用：gen <明文>  —— 注意命令行参数会进入 shell 历史。");
            return null;
        }
        char[] chars = console.readPassword("请输入口令（不回显）: ");
        return chars == null ? null : new String(chars);
    }

    /** 交互式读取口令并要求输入两次，避免打错。 */
    private static String readSecretTwice() {
        Console console = System.console();
        if (console == null) {
            System.err.println("当前环境没有可用的控制台（无法安全读取口令）。");
            System.err.println("请改用：gen <明文>  —— 注意命令行参数会进入 shell 历史。");
            return null;
        }
        char[] first = console.readPassword("请输入新口令（不回显）: ");
        if (first == null) {
            return null;
        }
        char[] second = console.readPassword("请再输入一次确认: ");
        if (second == null) {
            return null;
        }
        String a = new String(first);
        String b = new String(second);
        if (!a.equals(b)) {
            System.err.println("两次输入不一致，已中止。");
            return null;
        }
        return a;
    }

    private static void usage() {
        System.out.println("""
                simple-shop 口令哈希工具（BCrypt，强度 %d）

                用法：
                  gen [明文]            生成哈希；省略明文则交互式输入（不回显，推荐）
                  verify <哈希> [明文]   校验明文与哈希是否匹配

                示例（PowerShell，Windows 上 classpath 分隔符为 ';'）：
                  mvn -q compile
                  mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
                  $cp = "target\\classes;" + (Get-Content target\\cp.txt -Raw)
                  java -cp $cp src/main/java/com/simpleshop/security/PasswordHashTool.java gen
                """.formatted(PasswordHasher.STRENGTH));
    }
}
