package com.simpleshop.cart.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.cart.dto.CartItemData;
import com.simpleshop.cart.dto.CartListData;
import com.simpleshop.cart.entity.CartItem;
import com.simpleshop.cart.repository.CartItemRepository;
import com.simpleshop.cart.service.CartService;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.audit.OperationLogService;
import com.simpleshop.common.id.Ids;
import com.simpleshop.common.text.UtcIso8601;
import com.simpleshop.common.time.DatabaseTimeProvider;
import com.simpleshop.goods.api.GoodsQueryPort;
import com.simpleshop.goods.api.GoodsSnapshot;
import com.simpleshop.goods.enums.GoodsStatus;

/**
 * 购物车服务：行按「买家 + 商品」唯一，失效与否由商品状态在读取时派生，不落库。
 */
@Service
public class CartServiceImpl implements CartService {

    private final CartItemRepository cartItems;
    private final GoodsQueryPort goodsQueryPort;
    private final OperationLogService operationLog;

    public CartServiceImpl(
            CartItemRepository cartItems,
            GoodsQueryPort goodsQueryPort,
            OperationLogService operationLog) {
        this.cartItems = cartItems;
        this.goodsQueryPort = goodsQueryPort;
        this.operationLog = operationLog;
    }

    @Override
    @Transactional
    public void add(String buyerId, String goodsId) {
        String id = requireGoodsId(goodsId);
        GoodsSnapshot goods = goodsQueryPort.require(id);
        if (goods.status() != GoodsStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.CART_GOODS_NOT_SELECTABLE);
        }
        // 先判断是否已存在：upsert 的返回值在 Connector/J 默认配置下区分不出「新增 / 已存在」，
        // 直接按返回值写审计会把每次重复点击都记成新增。判断只用于审计，插入仍靠唯一键保幂等。
        boolean existed = cartItems.existsByBuyerIdAndGoodsId(buyerId, id);
        cartItems.insertIfAbsent(Ids.next("CI"), buyerId, id, DatabaseTimeProvider.utcNow());
        if (!existed) {
            operationLog.log(OperationLogService.ADD_CART, OperationLogService.TARGET_CART, id);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CartListData list(String buyerId) {
        List<CartItem> rows = cartItems.findByBuyerIdOrderByCreateAtDescIdDesc(buyerId);
        if (rows.isEmpty()) {
            return new CartListData(List.of());
        }
        Map<String, GoodsSnapshot> snapshots = goodsQueryPort.snapshotsOf(
                rows.stream().map(CartItem::getGoodsId).toList());
        List<CartItemData> items = new ArrayList<>(rows.size());
        for (CartItem row : rows) {
            GoodsSnapshot goods = snapshots.get(row.getGoodsId());
            if (goods == null) {
                // 商品行永不物理删除，正常取不到说明数据被外部改坏，跳过而不是整页失败。
                continue;
            }
            items.add(new CartItemData(
                    goods.id(),
                    goods.name(),
                    goods.price(),
                    goods.stock(),
                    goods.status(),
                    goods.offShelfReason(),
                    goods.mainImageUrl(),
                    goods.status() == GoodsStatus.ON_SALE,
                    UtcIso8601.of(row.getCreateAt())));
        }
        return new CartListData(items);
    }

    @Override
    @Transactional
    public void remove(String buyerId, String goodsId) {
        String id = requireGoodsId(goodsId);
        // 行不存在时返回 0，同样算成功：重复移除必须幂等。
        int deleted = cartItems.deleteByBuyerIdAndGoodsId(buyerId, id);
        if (deleted > 0) {
            operationLog.log(OperationLogService.REMOVE_CART, OperationLogService.TARGET_CART, id);
        }
    }

    private static String requireGoodsId(String goodsId) {
        String id = goodsId == null ? "" : goodsId.trim();
        if (id.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        return id;
    }
}
