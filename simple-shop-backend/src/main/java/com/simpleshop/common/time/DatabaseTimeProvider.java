package com.simpleshop.common.time;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

/**
 * 全项目唯一的 UTC 时间来源。数据库中的时间列一律写入 UTC，禁止直接使用
 * {@code LocalDateTime.now()} 或 {@code new Date()} 入库。
 */
@Component
public class DatabaseTimeProvider {

    public static final ZoneOffset STORAGE_ZONE = ZoneOffset.UTC;

    public LocalDateTime nowUtc() {
        return utcNow();
    }

    /** 供 JPA 实体生命周期回调使用（回调中无法依赖注入）。 */
    public static LocalDateTime utcNow() {
        return LocalDateTime.now(STORAGE_ZONE);
    }
}
