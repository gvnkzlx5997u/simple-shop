package com.simpleshop.account.api;

import com.simpleshop.common.api.PageResponse;

/**
 * 客户购买历史端口：account 定义、history 实现。
 * 购买历史是当前表终态意向与历史意向表的并集，数据与查询都在 history。
 */
public interface CustomerPurchaseHistoryPort {

    PageResponse<CustomerPurchaseEntryData> history(String buyerId, Integer page, Integer pageSize);
}
