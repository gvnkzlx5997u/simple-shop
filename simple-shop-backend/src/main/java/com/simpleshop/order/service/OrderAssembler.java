package com.simpleshop.order.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.simpleshop.common.text.UtcIso8601;
import com.simpleshop.order.dto.BuyerRefData;
import com.simpleshop.order.dto.OrderData;
import com.simpleshop.order.dto.OrderItemData;
import com.simpleshop.order.dto.OrderPlacedData;
import com.simpleshop.order.dto.OrderStatusData;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.entity.OrderItem;

/**
 * 订单实体到响应 DTO 的映射。时间统一走 UTC 的 ISO-8601。
 *
 * <p>买家用户名由调用方实时读账号后传进来，订单本身不存用户名快照。
 */
@Component
public class OrderAssembler {

    public OrderPlacedData placed(Order order) {
        return new OrderPlacedData(
                order.getId(),
                order.getOrderNo(),
                order.getTotalAmount(),
                order.getStatus(),
                UtcIso8601.of(order.getCreateAt()));
    }

    public OrderStatusData status(Order order) {
        return new OrderStatusData(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                UtcIso8601.of(order.getUpdateAt()));
    }

    public OrderData data(Order order, String buyerUsername, long itemCount) {
        return new OrderData(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                order.getTotalAmount(),
                (int) itemCount,
                UtcIso8601.of(order.getCreateAt()),
                UtcIso8601.of(order.getUpdateAt()),
                new BuyerRefData(order.getBuyerId(), buyerUsername));
    }

    public OrderItemData item(OrderItem item) {
        return new OrderItemData(
                item.getGoodsId(),
                item.getGoodsName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }
}
