package com.simpleshop.inventory.api;

/**
 * 一笔扣减里某个商品的库存缺口。{@code required} 是本单需要的数量，
 * {@code available} 是加锁后读到的可用库存；两者之差就是缺口。
 */
public record StockShortage(
        String goodsId,
        int required,
        int available) {
}
