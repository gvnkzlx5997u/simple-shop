package com.simpleshop.order.service;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.order.api.AftersaleTarget;
import com.simpleshop.order.api.OrderItemSnapshot;
import com.simpleshop.order.api.OrderTradePort;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.entity.OrderItem;
import com.simpleshop.order.repository.OrderItemRepository;
import com.simpleshop.order.repository.OrderRepository;

/**
 * 交易只读端口实现：按订单项取售后需要的快照与订单事实。
 *
 * <p>不加锁：售后申请只读一次订单状态，真正的冲突判断在售后完成时用订单项行锁串行化。
 */
@Component
public class OrderTradeAdapter implements OrderTradePort {

    private final OrderItemRepository orderItems;
    private final OrderRepository orders;

    public OrderTradeAdapter(OrderItemRepository orderItems, OrderRepository orders) {
        this.orderItems = orderItems;
        this.orders = orders;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AftersaleTarget> findAftersaleTarget(String buyerId, String orderItemId) {
        if (buyerId == null || buyerId.isBlank() || orderItemId == null || orderItemId.isBlank()) {
            return Optional.empty();
        }
        OrderItem item = orderItems.findById(orderItemId.trim()).orElse(null);
        if (item == null) {
            return Optional.empty();
        }
        Order order = item.getOrder();
        if (!order.belongsTo(buyerId)) {
            return Optional.empty();
        }
        return Optional.of(new AftersaleTarget(
                order.getId(),
                order.getOrderNo(),
                item.getId(),
                item.getGoodsId(),
                item.getGoodsName(),
                item.getUnitPrice(),
                item.getQuantity(),
                order.getStatus(),
                order.getPaymentChannel(),
                order.getBuyerId()));
    }

    @Override
    @Transactional
    public Optional<OrderItemSnapshot> lockOrderItem(String orderItemId) {
        if (orderItemId == null || orderItemId.isBlank()) {
            return Optional.empty();
        }
        return orderItems.findWithLockById(orderItemId.trim())
                .map(item -> new OrderItemSnapshot(
                        item.getId(), item.getUnitPrice(), item.getQuantity()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findOrderIdByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return Optional.empty();
        }
        return orders.findByOrderNo(orderNo.trim()).map(Order::getId);
    }
}
