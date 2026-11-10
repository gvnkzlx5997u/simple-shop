package com.simpleshop.cart.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.cart.entity.CartItem;
import com.simpleshop.cart.repository.CartItemRepository;
import com.simpleshop.cart.service.CartToFavoriteService;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.favorite.api.FavoritePort;
import com.simpleshop.goods.api.GoodsQueryPort;
import com.simpleshop.goods.api.GoodsSnapshot;
import com.simpleshop.goods.enums.GoodsStatus;

/**
 * 转收藏：先校验、再写收藏、最后删购物车行，同一事务。
 *
 * <p>校验放在最前面而不是边写边判：只要入参里有一件不可选，就整批拒绝，一行都不动。
 * 依赖 favorite 的 api 端口，不碰它的 service 与仓储。
 */
@Service
public class CartToFavoriteServiceImpl implements CartToFavoriteService {

    private static final Logger log = LoggerFactory.getLogger(CartToFavoriteServiceImpl.class);

    private final CartItemRepository cartItems;
    private final FavoritePort favoritePort;
    private final GoodsQueryPort goodsQueryPort;

    public CartToFavoriteServiceImpl(
            CartItemRepository cartItems,
            FavoritePort favoritePort,
            GoodsQueryPort goodsQueryPort) {
        this.cartItems = cartItems;
        this.favoritePort = favoritePort;
        this.goodsQueryPort = goodsQueryPort;
    }

    @Override
    @Transactional
    public int moveToFavorites(String buyerId, Collection<String> goodsIds) {
        List<String> ids = normalize(goodsIds);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }

        // 只转本购物车真实存在的行：入参里不在车里的商品直接忽略，不顺手写进收藏。
        // 口径依据是「写入收藏、移除**对应**购物车行」——没有对应行就没有可转的东西；
        // 顺带让 moved_count 恒等于真正转过去的件数，不会出现「报 1 件、收藏多 2 件」。
        List<String> inCart = cartItems.findByBuyerIdAndGoodsIdIn(buyerId, ids).stream()
                .map(CartItem::getGoodsId)
                .toList();
        if (inCart.isEmpty()) {
            return 0;
        }

        Map<String, GoodsSnapshot> snapshots = goodsQueryPort.snapshotsOf(inCart);
        List<String> rejected = new ArrayList<>();
        for (String id : inCart) {
            GoodsSnapshot goods = snapshots.get(id);
            if (goods == null || goods.status() != GoodsStatus.ON_SALE) {
                rejected.add(id);
            }
        }
        if (!rejected.isEmpty()) {
            // 拒绝时要把不可选的那几件指出来：商品 ID 拼进 message，前端按 goods_id 定位到具体行。
            log.info("move-to-favorites rejected, buyer={} goods={}", buyerId, rejected);
            throw new BusinessException(
                    ErrorCode.CART_TO_FAVORITE_REJECTED,
                    ErrorCode.CART_TO_FAVORITE_REJECTED.message()
                            + ": " + summarize(rejected));
        }
        favoritePort.addAll(buyerId, inCart);
        return cartItems.deleteByBuyerIdAndGoodsIdIn(buyerId, inCart);
    }

    /** 去空、去重并保持入参顺序；重复 ID 只处理一次。 */
    private static List<String> normalize(Collection<String> goodsIds) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (goodsIds != null) {
            for (String raw : goodsIds) {
                if (raw == null) {
                    continue;
                }
                String id = raw.trim();
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        return List.copyOf(ids);
    }

    /** 错误消息里最多列出 5 个商品 ID，避免整批商品把消息撑成一篇。 */
    private static String summarize(List<String> rejected) {
        int limit = 5;
        if (rejected.size() <= limit) {
            return String.join(", ", rejected);
        }
        return String.join(", ", rejected.subList(0, limit)) + " (+" + (rejected.size() - limit) + " more)";
    }
}
