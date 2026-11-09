package com.simpleshop.order.service;

import com.simpleshop.order.dto.OrderPlacedData;
import com.simpleshop.order.dto.OrderStatusData;
import com.simpleshop.order.dto.PlaceOrderRequest;

/**
 * 买家下单与取消。
 */
public interface BuyerOrderService {

    /**
     * 提交订单：整单成功或整单拒绝，不做部分受理。
     *
     * <p>下单只校验商品状态与库存，不锁库存行、不预占库存；库存以成交完成时为准。
     */
    OrderPlacedData place(String buyerId, PlaceOrderRequest request);

    /**
     * 买家取消自己的订单：进入已发货之前可取消，整单取消、不影响库存。
     *
     * <p>订单不属于该买家时按「订单不存在」处理，不泄露他人订单是否存在。
     * 已取消的订单重复取消按成功返回，只为防抖，不是可依赖的业务规则。
     */
    OrderStatusData cancel(String buyerId, String orderId);
}
