package com.simpleshop.history.service;

import com.simpleshop.common.api.PageResponse;
import com.simpleshop.history.dto.BuyerHistoryEntryData;

/**
 * 买家历史：当前表已终态意向与历史意向表的并集，只读。
 */
public interface BuyerHistoryService {

    PageResponse<BuyerHistoryEntryData> history(
            String buyerId, Integer page, Integer pageSize);
}
