package com.simpleshop.goods.api;

import java.util.Collection;
import java.util.List;

/**
 * 商品只读查询端口：由 goods 实现，供订单、购物车、收藏校验商品存在性并取下单快照。
 *
 * <p>调用方只能拿到 {@link GoodsSnapshot}，不感知商品实体与懒加载。
 */
public interface GoodsQueryPort {

    GoodsSnapshot require(String goodsId);

    /**
     * 下单前按商品行加写锁并取下单快照。
     *
     * <p>加锁顺序固定为商品 ID 升序：两笔订单交叉下单时不会互相等待成环。
     * 不存在的商品不出现在返回列表里，由调用方判定为「商品不存在」。
     */
    List<GoodsSnapshot> lockForOrder(Collection<String> goodsIds);
}
