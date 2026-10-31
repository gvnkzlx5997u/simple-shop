package com.simpleshop.goods.service;

import com.simpleshop.common.api.PageResponse;
import com.simpleshop.goods.dto.GoodsDetailData;
import com.simpleshop.goods.dto.GoodsSummaryData;

/**
 * 公共商品查询：列表/搜索只返回在售商品。
 */
public interface BuyerGoodsQueryService {

    PageResponse<GoodsSummaryData> list(
            String keyword, String categoryId, Integer page, Integer pageSize);

    GoodsDetailData detail(String id);
}
