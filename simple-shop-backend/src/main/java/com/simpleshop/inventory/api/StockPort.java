package com.simpleshop.inventory.api;

import java.util.Collection;
import java.util.List;
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

    /**
     * 成交时按明细数量一次性扣减：先按商品 ID 升序锁住全部库存行，再整体校验，最后统一扣减。
     *
     * <p>全成或全败：返回非空列表表示有缺口，此时一行都没扣，调用方整单拒绝；
     * 返回空列表表示全部扣减成功。不出现部分扣减与负库存。
     */
    List<StockShortage> deductAll(Map<String, Integer> quantities);
}
