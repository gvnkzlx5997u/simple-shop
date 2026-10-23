package com.simpleshop.common.auth;

import java.io.Console;

/**
 * 密码哈希命令行工具：生成种子账号哈希，或核对明文与库中哈希是否匹配。
 *
 * <p>省略明文参数时走交互式输入且不回显。命令行参数会进入 shell 历史与进程列表，
 * 仅在脚本场景使用。
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
        System.out.println("self check matches = " + PasswordHasher.matches(raw, hash));
        System.out.println();
        System.out.println("Update the seller seed account:");
        System.out.println("  UPDATE seller_account");
        System.out.println("     SET password = '" + hash + "',");
        System.out.println("         update_at = UTC_TIMESTAMP()");
        System.out.println("   WHERE account = 'seller';");
        System.out.println();
        System.out.println("Or replace the hash literal in the seed migration and rebuild the database.");
    }

    private static void verify(String[] args) {
        if (args.length < 2) {
            System.out.println("verify requires the stored BCrypt hash.");
            usage();
            return;
        }
        String hash = args[1];
        System.out.println("hash format valid = " + PasswordHasher.isBcryptHash(hash));
        String raw = args.length > 2 ? args[2] : readSecret();
        if (raw == null) {
            return;
        }
        System.out.println("matches = " + PasswordHasher.matches(raw, hash));
    }

    private static String readSecret() {
        Console console = System.console();
        if (console == null) {
            System.err.println("No interactive console is available.");
            System.err.println("Use: gen <password> (the argument will be visible to other processes).");
            return null;
        }
        char[] chars = console.readPassword("Password (not echoed): ");
        return chars == null ? null : new String(chars);
    }

    private static String readSecretTwice() {
        Console console = System.console();
        if (console == null) {
            System.err.println("No interactive console is available.");
            System.err.println("Use: gen <password> (the argument will be visible to other processes).");
            return null;
        }
        char[] first = console.readPassword("New password (not echoed): ");
        if (first == null) {
            return null;
        }
        char[] second = console.readPassword("Repeat password: ");
        if (second == null) {
            return null;
        }
        String a = new String(first);
        String b = new String(second);
        if (!a.equals(b)) {
            System.err.println("Passwords do not match, aborted.");
            return null;
        }
        return a;
    }

    private static void usage() {
        System.out.println("""
                simple-shop password hash tool (BCrypt, strength %d)

                usage:
                  gen [password]            generate a hash; omit password to enter it interactively
                  verify <hash> [password]  check a plaintext password against a stored hash

                example (PowerShell):
                  mvn -q compile
                  mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
                  $cp = "target\\classes;" + (Get-Content target\\cp.txt -Raw)
                  java -cp $cp src/main/java/com/simpleshop/common/auth/PasswordHashTool.java gen
                """.formatted(PasswordHasher.STRENGTH));
    }
}
