package com.simpleshop.goods.api;

/**
 * 未完成订单端口：由 order 实现，供 goods 在手动下架与手动改库存为 0 前判断能否执行。
 *
 * <p>反转依赖是为了不让 goods 依赖 order：商品不知道订单存在，只在需要时问一句
 * 「这个商品还有没走完的订单吗」。
 */
public interface UnfinishedOrderPort {

    /**
     * 商品是否被未完成订单引用。未完成 = 待确认 / 已确认 / 备货完成 / 已发货四态；
     * 已取消与交易完成都不算。
     */
    boolean hasUnfinishedOrder(String goodsId);
}
