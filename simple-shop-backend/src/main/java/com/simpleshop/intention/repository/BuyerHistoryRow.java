package com.simpleshop.intention.repository;

import java.time.LocalDateTime;

/**
 * 买家历史的并集查询行：当前表已终态意向与历史意向表的统一形状。
 * 列别名与 native 查询保持一致。
 */
public interface BuyerHistoryRow {

    String getId();

    String getGoodsName();

    LocalDateTime getCreateAt();

    String getResult();

    LocalDateTime getFinishAt();
}
