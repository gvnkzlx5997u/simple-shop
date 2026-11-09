package com.simpleshop.order.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.audit.OperationLogService;
import com.simpleshop.common.security.RateLimiter;
import com.simpleshop.goods.api.GoodsQueryPort;
import com.simpleshop.goods.api.GoodsSnapshot;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.inventory.api.StockPort;
import com.simpleshop.order.dto.OrderPlacedData;
import com.simpleshop.order.dto.OrderStatusData;
import com.simpleshop.order.dto.PlaceOrderItemRequest;
import com.simpleshop.order.dto.PlaceOrderRequest;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.entity.OrderLine;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.order.repository.OrderRepository;
import com.simpleshop.order.service.BuyerOrderService;
import com.simpleshop.order.service.OrderAssembler;
import com.simpleshop.order.support.OrderNoGenerator;

/**
 * 下单：先锁商品行再校验，避免「校验通过后商品被下架」。
 *
 * <p>事务顺序固定为：校验明细形状 → 按 ID 升序锁商品行 → 校验在售 → 读库存 → 取下单快照 →
 * 算总额并落库。库存只读不锁：本期没有预占，真正的扣减发生在交易完成时。
 *
 * <p>拒绝口径：任一明细不满足就整单拒绝、不写任何数据，并按「为什么」返回已有业务码——
 * 商品不存在 20011、商品已下架 20019、库存不足 20015。订单域自己的状态类错误码
 * （订单不存在、状态不允许、不可取消、完成缺货）留给后续状态迁移使用。
 *
 * <p>同一订单内重复商品按参数错误拒绝而不是合并：正常入口是购物车勾选，一个商品只会
 * 提交一行；出现两行同商品说明调用方把明细拼错了，属于调用错误而不是业务分支。
 */
@Service
public class BuyerOrderServiceImpl implements BuyerOrderService {

    private final OrderRepository orders;
    private final GoodsQueryPort goodsQuery;
    private final StockPort stockPort;
    private final OrderNoGenerator orderNumbers;
    private final OperationLogService audit;
    private final RateLimiter rateLimiter;
    private final OrderAssembler assembler;

    public BuyerOrderServiceImpl(
            OrderRepository orders,
            GoodsQueryPort goodsQuery,
            StockPort stockPort,
            OrderNoGenerator orderNumbers,
            OperationLogService audit,
            RateLimiter rateLimiter,
            OrderAssembler assembler) {
        this.orders = orders;
        this.goodsQuery = goodsQuery;
        this.stockPort = stockPort;
        this.orderNumbers = orderNumbers;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.assembler = assembler;
    }

    @Transactional
    public OrderPlacedData place(String buyerId, PlaceOrderRequest request) {
        // 限流是保护措施，不是业务规则：只给一个很高的兜底阈值，按买家计。
        if (!rateLimiter.tryAcquire(RateLimiter.Scope.ORDER, buyerId)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        List<PlaceOrderItemRequest> requested = request == null || request.items() == null
                ? List.of()
                : request.items();
        requireWellFormed(requested);

        List<OrderLine> lines = resolveLines(requested);
        Order order = orders.saveAndFlush(Order.place(orderNumbers.next(), buyerId, lines));
        audit.log(OperationLogService.CREATE_ORDER,
                OperationLogService.TARGET_ORDER, order.getId());
        return assembler.placed(order);
    }

    @Transactional
    public OrderStatusData cancel(String buyerId, String orderId) {
        // 先按归属过滤再判断状态：别人的订单一律当作不存在，避免探测订单是否存在。
        Order order = orders.findWithLockById(orderId)
                .filter(candidate -> candidate.belongsTo(buyerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 重复取消按成功返回，只是为了防抖：按钮连点或网络重试不该报错。
        if (order.getStatus() == OrderStatus.CANCELED) {
            return assembler.status(order);
        }
        if (!order.canCancelByBuyer()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELABLE,
                    "order " + order.getOrderNo() + " cannot be canceled by the buyer in status "
                            + order.getStatus());
        }
        order.cancelByBuyer();
        return persistCancellation(order);
    }

    private OrderStatusData persistCancellation(Order order) {
        orders.saveAndFlush(order);
        audit.log(OperationLogService.ORDER_CANCEL,
                OperationLogService.TARGET_ORDER, order.getId());
        return assembler.status(order);
    }

    /**
     * 形状校验：空明细、重复商品、数量非法都在碰数据库之前拒绝。
     *
     * <p>数量小于 1 本来就会被请求校验拦下，这里再挡一次，是为了让内部调用
     * 也拿不到非法明细。
     */
    private void requireWellFormed(List<PlaceOrderItemRequest> requested) {
        if (requested.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID, "an order must contain at least one item");
        }

        Set<String> seen = new HashSet<>();
        for (PlaceOrderItemRequest item : requested) {
            if (item == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "order item must not be null");
            }
            String goodsId = item.goodsId() == null ? "" : item.goodsId().trim();
            if (!seen.add(goodsId)) {
                throw new BusinessException(
                        ErrorCode.PARAM_INVALID, "duplicate goods in one order: " + goodsId);
            }
            if (item.quantity() == null || item.quantity() < 1) {
                throw new BusinessException(
                        ErrorCode.PARAM_INVALID, "quantity must be at least 1 for goods " + goodsId);
            }
        }
    }

    /** 锁商品行、校验在售与库存，并把通过的明细转成下单快照。 */
    private List<OrderLine> resolveLines(List<PlaceOrderItemRequest> requested) {
        List<String> goodsIds = requested.stream()
                .map(item -> item.goodsId().trim())
                .toList();
        Map<String, GoodsSnapshot> snapshots = goodsQuery.lockForOrder(goodsIds).stream()
                .collect(Collectors.toMap(GoodsSnapshot::id, Function.identity()));

        Map<String, Integer> stock = stockPort.availableAll(snapshots.keySet());

        List<OrderLine> lines = new ArrayList<>(requested.size());
        for (PlaceOrderItemRequest item : requested) {
            String goodsId = item.goodsId().trim();
            GoodsSnapshot snapshot = snapshots.get(goodsId);
            if (snapshot == null) {
                throw new BusinessException(
                        ErrorCode.GOODS_NOT_FOUND, "goods not found: " + goodsId);
            }
            if (snapshot.status() != GoodsStatus.ON_SALE) {
                throw new BusinessException(
                        ErrorCode.GOODS_NOT_ON_SALE, "goods is not on sale: " + goodsId);
            }
            if (item.quantity() > stock.getOrDefault(goodsId, 0)) {
                throw new BusinessException(
                        ErrorCode.STOCK_INVALID, "insufficient stock for goods " + goodsId);
            }
            lines.add(new OrderLine(goodsId, snapshot.name(), snapshot.price(), item.quantity()));
        }
        return lines;
    }
}
