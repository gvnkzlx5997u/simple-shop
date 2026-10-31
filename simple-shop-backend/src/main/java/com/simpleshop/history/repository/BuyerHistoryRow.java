package com.simpleshop.history.repository;

import java.time.LocalDateTime;

/**
 * 买家历史的并集查询行：当前表已终态意向与历史意向表的统一形状。
 */
public interface BuyerHistoryRow {

    String getId();

    String getGoodsName();

    LocalDateTime getCreateAt();

    String getResult();

    LocalDateTime getFinishAt();
}
