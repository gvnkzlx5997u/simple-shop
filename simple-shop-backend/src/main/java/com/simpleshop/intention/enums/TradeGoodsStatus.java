package com.simpleshop.intention.enums;

/**
 * 交易裁决后的商品去向。
 *
 * <p>ON_SALE：库存有余，商品回到在售；ARCHIVED：库存扣到 0，商品与全部意向转入历史。
 * 与 {@code GoodsStatus} 分开：{@code ARCHIVED} 不是当前商品表的状态。
 */
public enum TradeGoodsStatus {
    ON_SALE,
    ARCHIVED
}
