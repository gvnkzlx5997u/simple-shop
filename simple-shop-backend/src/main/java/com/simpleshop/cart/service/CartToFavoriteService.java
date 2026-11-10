package com.simpleshop.cart.service;

import java.util.Collection;

/**
 * 购物车 → 收藏的整批搬运。
 */
public interface CartToFavoriteService {

    /**
     * 单事务：写收藏（幂等）→ 删购物车行。入参含已下架商品时整批拒绝，一行都不动。
     *
     * @return 实际从购物车删除的行数
     */
    int moveToFavorites(String buyerId, Collection<String> goodsIds);
}
