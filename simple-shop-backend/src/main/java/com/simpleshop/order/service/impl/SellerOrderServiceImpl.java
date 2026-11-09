package com.simpleshop.order.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.audit.OperationLogService;
import com.simpleshop.goods.api.GoodsQueryPort;
import com.simpleshop.goods.api.GoodsSalePort;
import com.simpleshop.inventory.api.StockPort;
import com.simpleshop.inventory.api.StockShortage;
import com.simpleshop.order.dto.OrderStatusData;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.entity.OrderItem;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.order.repository.OrderRepository;
import com.simpleshop.order.service.OrderAssembler;
import com.simpleshop.order.service.SellerOrderService;

/**
 * 卖家推进与取消：每次只能前进一格，状态守卫在后端，不依赖前端按钮。
 *
 * <p>推进与取消都先锁订单行，重复提交的第二个请求拿到锁后会看到新状态，
 * 因而得到「状态不允许」而不是把订单推两格。
 *
 * <p>取消只写进操作日志（操作者与时间），订单上不落取消人与取消时间字段。
 */
@Service
public class SellerOrderServiceImpl implements SellerOrderService {

    private final OrderRepository orders;
    private final OperationLogService audit;
    private final OrderAssembler assembler;
    private final GoodsQueryPort goodsQuery;
    private final GoodsSalePort goodsSale;
    private final StockPort stockPort;

    public SellerOrderServiceImpl(
            OrderRepository orders,
            OperationLogService audit,
            OrderAssembler assembler,
            GoodsQueryPort goodsQuery,
            GoodsSalePort goodsSale,
            StockPort stockPort) {
        this.orders = orders;
        this.audit = audit;
        this.assembler = assembler;
        this.goodsQuery = goodsQuery;
        this.goodsSale = goodsSale;
        this.stockPort = stockPort;
    }

    @Transactional
    public OrderStatusData confirm(String orderId) {
        Order order = lock(orderId);
        if (!order.canConfirm()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "order " + order.getOrderNo() + " is not pending confirmation");
        }
        order.confirm();
        return persist(order, OperationLogService.ORDER_CONFIRM);
    }

    /** 备货完成只是流程状态，不代表库存充足，因此不查库存。 */
    @Transactional
    public OrderStatusData prepare(String orderId) {
        Order order = lock(orderId);
        if (!order.canPrepare()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "order " + order.getOrderNo() + " is not confirmed");
        }
        order.prepare();
        return persist(order, OperationLogService.ORDER_PREPARE);
    }

    @Transactional
    public OrderStatusData ship(String orderId) {
        Order order = lock(orderId);
        if (!order.canShip()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "order " + order.getOrderNo() + " is not prepared");
        }
        order.ship();
        return persist(order, OperationLogService.ORDER_SHIP);
    }

    /**
     * 交易完成：扣库存与改订单状态在同一事务里，全成或全败。
     *
     * <p>先锁订单行，再按 ID 升序锁商品行、库存行；库存只在这个事务里扣一次，
     * 因此重复提交的第二个请求会先被状态守卫挡下，不会重复扣库存。
     */
    @Transactional
    public OrderStatusData complete(String orderId) {
        Order order = lock(orderId);
        if (!order.canComplete()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "order " + order.getOrderNo() + " is not shipped");
        }

        Map<String, Integer> required = requiredQuantities(order);
        goodsQuery.lockForOrder(required.keySet());
        List<StockShortage> shortages = stockPort.deductAll(required);
        if (!shortages.isEmpty()) {
            // 整单拒绝：上面一行都没扣，订单仍是已发货，卖家补货后可以重试。
            throw new BusinessException(
                    ErrorCode.ORDER_STOCK_SHORTAGE, shortageMessage(shortages));
        }
        goodsSale.markSoldOut(required.keySet());
        order.complete();
        return persist(order, OperationLogService.ORDER_COMPLETE);
    }

    @Transactional
    public OrderStatusData cancel(String orderId) {
        Order order = lock(orderId);
        // 重复取消按成功返回，只是为了防抖；不写审计，避免日志里出现不存在的状态变更。
        if (order.getStatus() == OrderStatus.CANCELED) {
            return assembler.status(order);
        }
        if (!order.canCancelBySeller()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELABLE,
                    "order " + order.getOrderNo() + " cannot be canceled in status "
                            + order.getStatus());
        }
        order.cancelBySeller();
        return persist(order, OperationLogService.ORDER_CANCEL);
    }

    private static Map<String, Integer> requiredQuantities(Order order) {
        Map<String, Integer> required = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            required.merge(item.getGoodsId(), item.getQuantity(), Integer::sum);
        }
        return required;
    }

    /** 只报商品与数量缺口，不带名称、单价等完整订单项信息。 */
    private static String shortageMessage(List<StockShortage> shortages) {
        String detail = shortages.stream()
                .map(shortage -> shortage.goodsId() + " needs " + shortage.required()
                        + ", has " + shortage.available())
                .collect(Collectors.joining("; "));
        return "insufficient stock: " + detail;
    }

    private Order lock(String orderId) {
        String id = orderId == null ? "" : orderId.trim();
        return orders.findWithLockById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    private OrderStatusData persist(Order order, String operation) {
        orders.saveAndFlush(order);
        audit.log(operation, OperationLogService.TARGET_ORDER, order.getId());
        return assembler.status(order);
    }
}
