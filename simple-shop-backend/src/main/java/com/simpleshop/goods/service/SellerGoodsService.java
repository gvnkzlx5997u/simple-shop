package com.simpleshop.goods.service;

import com.simpleshop.goods.dto.GoodsDetailData;
import com.simpleshop.goods.dto.GoodsPublishRequest;
import com.simpleshop.goods.dto.ReListRequest;

/**
 * 卖家商品写操作：发布、库存、上下架与改挂。
 */
public interface SellerGoodsService {

    GoodsDetailData publish(GoodsPublishRequest request);

    GoodsDetailData updateStock(String id, Integer stockValue);

    GoodsDetailData offShelf(String id);

    GoodsDetailData reList(String id, ReListRequest request);

    GoodsDetailData changeCategory(String id, String categoryId);
}
