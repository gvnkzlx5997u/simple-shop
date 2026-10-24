package com.simpleshop.intention.repository;

import java.time.LocalDateTime;

/**
 * 卖家客户购买历史的并集查询行：与买家历史同构，多出商品 ID 与失败原因。
 */
public interface CustomerPurchaseRow {

    String getId();

    String getGoodsId();

    String getGoodsName();

    LocalDateTime getCreateAt();

    String getResult();

    String getFailType();

    LocalDateTime getFinishAt();
}
