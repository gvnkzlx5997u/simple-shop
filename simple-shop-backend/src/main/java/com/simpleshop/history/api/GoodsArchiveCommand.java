package com.simpleshop.history.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 归档商品快照：由 intention 从当前商品构建，history 只负责写入。
 */
public record GoodsArchiveCommand(
        String id,
        String name,
        BigDecimal price,
        int finalStock,
        String categoryL1Name,
        String categoryL2Name,
        String descriptionSnapshot,
        String imagesSnapshot,
        LocalDateTime createAt) {
}
