package com.simpleshop.history.service;

import com.simpleshop.common.api.PageResponse;
import com.simpleshop.history.dto.GoodsHistoryDetailData;
import com.simpleshop.history.dto.GoodsHistorySummaryData;

/**
 * 卖家历史查询：历史商品列表与详情，只读。
 */
public interface SellerHistoryService {

    PageResponse<GoodsHistorySummaryData> list(Integer page, Integer pageSize);

    GoodsHistoryDetailData detail(String id);
}
