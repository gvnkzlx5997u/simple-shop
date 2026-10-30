package com.simpleshop.goods.api;

/**
 * 商品生命周期端口：交易中的状态变更只能经此进入 goods，调用方必须已开启事务。
 */
public interface GoodsLifecyclePort {

    /** 共享锁 + 在售校验；供买家提交意向，允许并发提交。 */
    void requireOnSaleForNewIntention(String goodsId);

    /** 排他锁 + 状态预检，不改状态；调用方完成其余校验后再 startTrading。 */
    TradingStart lockForTrading(String goodsId);

    /** 排他锁：在售 → 交易中；必须在 lockForTrading 返回 STARTED 后调用。 */
    void startTrading(String goodsId);

    /** 排他锁 + 交易中校验。 */
    GoodsTradeView requireTrading(String goodsId);

    /** 排他锁：交易中 → 在售。 */
    void backToOnSale(String goodsId);

    GoodsArchiveSnapshot snapshotForArchive(String goodsId, int finalStock);

    /** 删除当前商品行；图片、描述块、库存、队列序号行由外键级联删除。 */
    void removeArchived(String goodsId);
}
