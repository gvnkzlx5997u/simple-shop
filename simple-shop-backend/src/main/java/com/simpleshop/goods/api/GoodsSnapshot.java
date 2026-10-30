package com.simpleshop.goods.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.simpleshop.goods.enums.GoodsStatus;

/**
 * 商品快照：跨功能只读契约，不携带实体。
 */
public record GoodsSnapshot(
        String id,
        String name,
        BigDecimal price,
        GoodsStatus status,
        String categoryL1Name,
        String categoryL2Name,
        LocalDateTime createAt) {
}
