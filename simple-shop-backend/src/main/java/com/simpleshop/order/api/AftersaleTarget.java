package com.simpleshop.order.api;

import java.math.BigDecimal;

import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.payment.api.PaymentChannel;

/**
 * 售后申请的定位结果：订单项快照 + 订单状态 + 支付渠道。
 *
 * <p>售后要判断「订单是否交易完成」「退款按什么渠道」，但它不该读订单表；
 * 这些字段由订单侧一次性取好，售后只做判断。
 */
public record AftersaleTarget(
        String orderId,
        String orderNo,
        String orderItemId,
        String goodsId,
        String goodsName,
        BigDecimal unitPrice,
        int quantity,
        OrderStatus orderStatus,
        PaymentChannel paymentChannel,
        String buyerId) {
}
