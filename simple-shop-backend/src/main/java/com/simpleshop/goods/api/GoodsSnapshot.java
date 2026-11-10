package com.simpleshop.goods.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.enums.OffShelfReason;

/**
 * 商品快照：跨功能只读契约，不携带实体。
 *
 * <p>末尾三个字段是为列表展示追加的：{@code mainImageUrl}（可能为空）、{@code stock}
 * （库存由独立聚合持有）、{@code offShelfReason}（区分「已售」「库存耗尽」「手动下架」的文案）。
 * 追加在末尾，不改变既有访问器与字段顺序，已有调用方无需修改。
 *
 * <p>{@code stock} 是**取快照那一刻**的库存，只用于展示「有货 / 缺货」。需要据此做写判断
 * （下单校验、成交扣减）时不能用它：快照与判断之间库存可能已被别人扣掉，必须另取库存聚合的
 * 实时值并在同一事务里加锁，否则会放过超卖。
 */
public record GoodsSnapshot(
        String id,
        String name,
        BigDecimal price,
        GoodsStatus status,
        String categoryL1Name,
        String categoryL2Name,
        LocalDateTime createAt,
        String mainImageUrl,
        int stock,
        OffShelfReason offShelfReason) {
}
