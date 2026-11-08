package com.simpleshop.goods.api;

/**
 * 商品只读查询端口：由 goods 实现，供订单、购物车、收藏校验商品存在性并取下单快照。
 *
 * <p>调用方只能拿到 {@link GoodsSnapshot}，不感知商品实体与懒加载。
 */
public interface GoodsQueryPort {

    GoodsSnapshot require(String goodsId);
}
