package com.simpleshop.goods.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品归档快照：成交售罄时由 goods 固化，intention 映射为 history 的命令。
 * descriptionSnapshot / imagesSnapshot 的 JSON 形状与 DescriptionBlockData / GoodsImageData 一致。
 */
public record GoodsArchiveSnapshot(
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
