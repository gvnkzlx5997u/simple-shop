package com.simpleshop.inventory.api;

import java.util.Collection;
import java.util.Map;

/**
 * 商品库存端口：库存是独立聚合，商品与交易只通过本端口读写。
 */
public interface StockPort {

    /** 发布商品时初始化库存；available 必须 >= 1。 */
    void initialize(String goodsId, int available);

    int availableOf(String goodsId);

    Map<String, Integer> availableAll(Collection<String> goodsIds);

    void setAvailable(String goodsId, int available);

    /** 成交扣减 1；返回扣减后的剩余，库存不足抛 STOCK_INVALID。 */
    int deductOne(String goodsId);
}
