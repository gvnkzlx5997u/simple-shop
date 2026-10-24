package com.simpleshop.goods.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品归档快照：成交售罄归档时从当前商品固化，写入历史商品表后不再依赖当前表。
 * descriptionSnapshot / imagesSnapshot 的 JSON 形状与 DescriptionBlockData / GoodsImageData 一致。
 */
public record GoodsArchiveSnapshot(
        String id,
        String name,
        BigDecimal price,
        int stock,
        String categoryL1Name,
        String categoryL2Name,
        String descriptionSnapshot,
        String imagesSnapshot,
        LocalDateTime createAt) {
}
