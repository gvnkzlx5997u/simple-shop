package com.simpleshop.order.service;

import com.simpleshop.order.dto.OrderPaymentData;
import com.simpleshop.order.dto.OrderPlacedData;
import com.simpleshop.order.dto.OrderStatusData;
import com.simpleshop.order.dto.PayOrderRequest;
import com.simpleshop.order.dto.PlaceOrderRequest;

/**
 * 买家下单与取消。
 */
public interface BuyerOrderService {

    /**
     * 提交订单：整单成功或整单拒绝，不做部分受理。
     *
     * <p>必须选一个已有收货地址，订单只存快照；下单只校验商品状态与库存，
     * 不锁库存行、不预占库存，库存以买家确认收货时为准。
     */
    OrderPlacedData place(String buyerId, PlaceOrderRequest request);

    /**
     * 支付自己的订单：待支付且未超时才受理，成功后进入已支付。
     *
     * <p>支付失败不改变订单：仍停在待支付，买家可以重试，直到超时取消。
     * 渠道结果由支付包给出，订单只负责落状态与记审计。
     */
    OrderPaymentData pay(String buyerId, String orderId, PayOrderRequest request);

    /**
     * 买家取消自己的订单：进入已发货之前可取消，整单取消、不影响库存。
     *
     * <p>订单不属于该买家时按「订单不存在」处理，不泄露他人订单是否存在。
     * 已取消的订单重复取消按成功返回，只为防抖，不是可依赖的业务规则。
     */
    OrderStatusData cancel(String buyerId, String orderId);
}
