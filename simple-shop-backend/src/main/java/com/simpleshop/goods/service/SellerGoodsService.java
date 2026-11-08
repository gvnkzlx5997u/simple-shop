package com.simpleshop.goods.service;

import com.simpleshop.goods.dto.GoodsDetailData;
import com.simpleshop.goods.dto.GoodsPublishRequest;

/**
 * 卖家商品写操作：发布、库存、下架与改挂。
 *
 * <p>手动下架是终态，没有重新上架接口；库存耗尽的商品补货后由库存写入自动上架。
 */
public interface SellerGoodsService {

    GoodsDetailData publish(GoodsPublishRequest request);

    GoodsDetailData updateStock(String id, Integer stockValue);

    GoodsDetailData offShelf(String id);

    GoodsDetailData changeCategory(String id, String categoryId);
}
