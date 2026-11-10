package com.simpleshop.favorite.service;

import com.simpleshop.common.api.PageResponse;
import com.simpleshop.favorite.dto.FavoriteItemData;

/**
 * 收藏：收藏、取消收藏、我的收藏。不做「收藏 → 加购 / 下单」（本期不做）。
 */
public interface FavoriteService {

    /** 收藏；商品不在售拒绝，重复收藏幂等。 */
    void add(String buyerId, String goodsId);

    /** 取消收藏；不要求商品在售（失效行也能删），行不存在也视为成功。 */
    void remove(String buyerId, String goodsId);

    PageResponse<FavoriteItemData> list(String buyerId, Integer page, Integer pageSize);

    /**
     * 当前买家是否收藏过这件商品，供商品详情页决定按钮显示「收藏」还是「已收藏」。
     *
     * <p>不校验商品是否存在、也不要求商品在售：问的是「收藏行在不在」。商品下架后行仍保留，
     * 此时照旧返回 true，由前端配合 {@code detail_available} 决定能否点进详情。
     */
    boolean favorited(String buyerId, String goodsId);
}
