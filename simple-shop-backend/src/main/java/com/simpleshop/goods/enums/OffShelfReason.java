package com.simpleshop.goods.enums;

/**
 * 下架原因，只在 status = OFF_SHELF 时非空。
 *
 * <p>能否复售由原因决定，而不是由库存数字决定：手动下架是终态，另外两种补货即可自动上架。
 */
public enum OffShelfReason {

    /** 卖家主动下架：终态，库存只读，类别仍可改挂。 */
    MANUAL,

    /** 卖家把库存改为 0 触发的自动下架。 */
    STOCK_ZERO,

    /** 订单交易完成扣减后库存为 0 触发的自动下架。 */
    SOLD_OUT;

    /** 手动下架不可复售；库存耗尽的两种原因补货即可上架。 */
    public boolean restockable() {
        return this != MANUAL;
    }
}
