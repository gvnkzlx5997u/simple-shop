package com.simpleshop.goods.api;

import com.simpleshop.goods.enums.GoodsStatus;

/**
 * 交易态商品视图：只含裁决需要的字段。
 */
public record GoodsTradeView(
        String id,
        String name,
        GoodsStatus status) {
}
