package com.simpleshop.goods.api;

import java.util.Collection;

/**
 * 成交后的商品状态联动：由 goods 实现，订单完成事务内调用。
 *
 * <p>库存是独立聚合，订单不直接改商品表；这里只负责把「已经卖空」的商品落到
 * 已下架（SOLD_OUT），与库存解耦。
 */
public interface GoodsSalePort {

    /**
     * 成交扣减后把库存为 0 的在售商品置为已下架（原因 SOLD_OUT）。
     *
     * <p>库存仍大于 0 的商品、已经下架的商品都不动：已下架商品保持原原因，
     * 不因为一次成交被改写。
     */
    void markSoldOut(Collection<String> goodsIds);
}
