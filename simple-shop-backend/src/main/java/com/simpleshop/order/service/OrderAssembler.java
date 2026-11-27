package com.simpleshop.order.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.simpleshop.common.text.UtcIso8601;
import com.simpleshop.order.dto.BuyerRefData;
import com.simpleshop.order.dto.OrderData;
import com.simpleshop.order.dto.OrderItemData;
import com.simpleshop.order.dto.OrderPaymentData;
import com.simpleshop.order.dto.OrderPlacedData;
import com.simpleshop.order.dto.OrderStatusData;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.entity.OrderItem;
import com.simpleshop.order.support.PaymentDeadline;

/**
 * 订单实体到响应 DTO 的映射。时间统一走 UTC 的 ISO-8601。
 *
 * <p>买家用户名由调用方实时读账号后传进来，订单本身不存用户名快照。
 * 退款合计也由调用方聚合后传入：它是「取消退款 + 售后退款」两个来源的和，
 * 装配器不负责去问谁要数据。
 */
@Component
public class OrderAssembler {

    private final PaymentDeadline deadlines;

    public OrderAssembler(PaymentDeadline deadlines) {
        this.deadlines = deadlines;
    }

    public OrderPlacedData placed(Order order) {
        return new OrderPlacedData(
                order.getId(),
                order.getOrderNo(),
                order.getTotalAmount(),
                order.getStatus(),
                UtcIso8601.of(order.getCreateAt()),
                payDeadline(order));
    }

    public OrderPaymentData payment(Order order, String redirectUrl) {
        return new OrderPaymentData(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                order.getPaymentChannel(),
                order.getPaidAt() == null ? null : UtcIso8601.of(order.getPaidAt()),
                redirectUrl);
    }

    public OrderStatusData status(Order order) {
        return new OrderStatusData(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                UtcIso8601.of(order.getUpdateAt()));
    }

    public OrderData data(
            Order order,
            String buyerUsername,
            long itemCount,
            BigDecimal refundedAmount,
            long aftersaleCount) {
        return new OrderData(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                order.getTotalAmount(),
                (int) itemCount,
                UtcIso8601.of(order.getCreateAt()),
                UtcIso8601.of(order.getUpdateAt()),
                new BuyerRefData(order.getBuyerId(), buyerUsername),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getReceiverAddress(),
                order.getPaymentChannel(),
                order.getPaidAt() == null ? null : UtcIso8601.of(order.getPaidAt()),
                payDeadline(order),
                order.getCarrier(),
                order.getTrackingNo(),
                order.getLogisticsRemark(),
                refundedAmount,
                aftersaleCount);
    }

    /** 只有待支付订单才有截止时间：已支付的订单再给一个倒计时只会误导人。 */
    private String payDeadline(Order order) {
        return order.canPay() ? UtcIso8601.of(deadlines.of(order)) : null;
    }

    public OrderItemData item(OrderItem item) {
        return new OrderItemData(
                item.getId(),
                item.getGoodsId(),
                item.getGoodsName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }
}
