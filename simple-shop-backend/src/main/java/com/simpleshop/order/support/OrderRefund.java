package com.simpleshop.order.support;

import org.springframework.stereotype.Component;

import com.simpleshop.order.entity.Order;
import com.simpleshop.payment.api.PaymentPort;

/**
 * 取消订单时的退款动作：只处理「已支付才退」，退完不动订单状态。
 *
 * <p>为什么先退款、再改状态：退款失败时订单必须原样不动（还停在已支付或已发货），
 * 让买家/卖家还能重试。反过来先置已取消再退款，失败就会留下「取消了但钱没退」的账。
 *
 * <p>调用方必须已经锁住订单行：重复取消的第二个请求会先看到 CANCELED 并短路，
 * 不会走到这里；支付包的流水唯一键是最后一道保险。
 */
@Component
public class OrderRefund {

    private final PaymentPort payments;

    public OrderRefund(PaymentPort payments) {
        this.payments = payments;
    }

    /** 已支付则原路全退；待支付订单不动支付包。 */
    public void refundIfPaid(Order order) {
        if (!order.isPaid()) {
            return;
        }
        payments.refundForCancel(
                order.getId(),
                order.getOrderNo(),
                order.getTotalAmount(),
                order.getPaymentChannel());
    }
}
