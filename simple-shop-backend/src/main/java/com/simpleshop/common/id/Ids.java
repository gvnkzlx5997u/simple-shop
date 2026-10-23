package com.simpleshop.common.id;

import java.util.UUID;

/**
 * 主键生成：前缀 + UUID。
 */
public final class Ids {

    private Ids() {
    }

    public static String next(String prefix) {
        return prefix + UUID.randomUUID();
    }
}
