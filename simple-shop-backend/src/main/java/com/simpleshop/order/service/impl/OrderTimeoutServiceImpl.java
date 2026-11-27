package com.simpleshop.order.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.common.audit.OperationLogService;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.order.repository.OrderRepository;
import com.simpleshop.order.service.OrderTimeoutService;
import com.simpleshop.order.support.PaymentDeadline;

/**
 * 超时取消的单笔实现：独立事务、先锁订单行再判断。
 *
 * <p>扫描任务在锁外只取到一批 ID，真正的判断在这里重做一遍：扫描与取消之间买家可能刚好付了款，
 * 只按扫描结果置状态会把已支付订单误杀。拿到行锁后看到的状态才是准的。
 */
@Service
public class OrderTimeoutServiceImpl implements OrderTimeoutService {

    private final OrderRepository orders;
    private final PaymentDeadline deadlines;
    private final OperationLogService audit;

    public OrderTimeoutServiceImpl(
            OrderRepository orders,
            PaymentDeadline deadlines,
            OperationLogService audit) {
        this.orders = orders;
        this.deadlines = deadlines;
        this.audit = audit;
    }

    @Override
    @Transactional
    public boolean cancelExpired(String orderId) {
        String id = orderId == null ? "" : orderId.trim();
        if (id.isEmpty()) {
            return false;
        }
        Order order = orders.findWithLockById(id).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return false;
        }
        if (!deadlines.isExpired(order)) {
            return false;
        }

        order.cancelForTimeout();
        orders.saveAndFlush(order);
        // 后台线程没有请求上下文，操作者写进 detail，便于日志里区分「系统取消」与人工取消。
        audit.log(OperationLogService.ORDER_TIMEOUT_CANCEL,
                OperationLogService.TARGET_ORDER, order.getId(), "SYSTEM");
        return true;
    }
}
