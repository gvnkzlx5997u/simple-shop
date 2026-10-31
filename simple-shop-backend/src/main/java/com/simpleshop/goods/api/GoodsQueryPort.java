package com.simpleshop.goods.api;

/**
 * 商品只读查询端口：由 goods 实现，供 intention 校验存在性与取归档快照。
 */
public interface GoodsQueryPort {

    GoodsSnapshot require(String goodsId);
}
