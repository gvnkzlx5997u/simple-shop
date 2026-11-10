package com.simpleshop.cart.service;

import com.simpleshop.cart.dto.CartListData;

/**
 * 购物车：只做加购、列表、移除三件事。改数量与勾选在前端，不经过本服务。
 */
public interface CartService {

    /** 加购；商品不在售拒绝，重复加购幂等。 */
    void add(String buyerId, String goodsId);

    CartListData list(String buyerId);

    /** 移除；行不存在也视为成功。 */
    void remove(String buyerId, String goodsId);
}
