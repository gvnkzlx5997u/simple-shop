package com.simpleshop.goods.service;

import com.simpleshop.common.api.PageResponse;
import com.simpleshop.goods.dto.GoodsDetailData;
import com.simpleshop.goods.dto.GoodsSummaryData;

/**
 * 卖家商品读操作：列表与详情。
 */
public interface SellerGoodsQueryService {

    PageResponse<GoodsSummaryData> list(
            String statusValue, String categoryId, String keyword, Integer page, Integer pageSize);

    GoodsDetailData detail(String id);
}
