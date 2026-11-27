package com.simpleshop.order.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.audit.OperationLogService;
import com.simpleshop.order.dto.OrderStatusData;
import com.simpleshop.order.dto.ShipLogisticsRequest;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.order.repository.OrderRepository;
import com.simpleshop.order.service.OrderAssembler;
import com.simpleshop.order.support.OrderRefund;
import com.simpleshop.order.service.SellerOrderService;

/**
 * 卖家推进与取消：每次只能前进一格，状态守卫在后端，不依赖前端按钮。
 *
 * <p>推进与取消都先锁订单行，重复提交的第二个请求拿到锁后会看到新状态，
 * 因而得到「状态不允许」而不是把订单推两格。
 *
 * <p>卖家不再有「交易完成」这个动作：成交由买家确认收货推进，卖家最多把订单推到已发货。
 * 取消只写进操作日志（操作者与时间），订单上不落取消人与取消时间字段。
 */
@Service
public class SellerOrderServiceImpl implements SellerOrderService {

    private final OrderRepository orders;
    private final OrderRefund refunds;
    private final OperationLogService audit;
    private final OrderAssembler assembler;

    public SellerOrderServiceImpl(
            OrderRepository orders,
            OrderRefund refunds,
            OperationLogService audit,
            OrderAssembler assembler) {
        this.orders = orders;
        this.refunds = refunds;
        this.audit = audit;
        this.assembler = assembler;
    }

    @Transactional
    public OrderStatusData confirm(String orderId) {
        Order order = lock(orderId);
        if (!order.canConfirm()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "order " + order.getOrderNo() + " is not paid");
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
    public OrderStatusData ship(String orderId, ShipLogisticsRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "logistics info is required");
        }
        Order order = lock(orderId);
        if (!order.canShip()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "order " + order.getOrderNo() + " is not prepared");
        }
        order.ship(request.carrier(), request.trackingNo(), request.logisticsRemark());
        return persist(order, OperationLogService.ORDER_SHIP);
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
        // 已支付（含已发货）的订单取消要原路退款；先退款再改状态，失败则订单不变。
        refunds.refundIfPaid(order);
        order.cancelBySeller();
        return persist(order, OperationLogService.ORDER_CANCEL);
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
