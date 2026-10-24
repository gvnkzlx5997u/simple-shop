package com.simpleshop.account.service;

import com.simpleshop.account.dto.CustomerPurchaseEntryData;
import com.simpleshop.common.api.PageResponse;

/**
 * 客户购买历史端口：account 定义、intention 实现。购买历史是当前表终态意向
 * 与历史意向表的并集，数据与查询都在 intention，account 不反向依赖。
 */
public interface CustomerPurchaseHistoryPort {

    PageResponse<CustomerPurchaseEntryData> history(String buyerId, Integer page, Integer pageSize);
}
