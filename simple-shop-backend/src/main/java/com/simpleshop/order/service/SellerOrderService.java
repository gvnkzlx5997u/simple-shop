package com.simpleshop.order.service;

import com.simpleshop.order.dto.OrderStatusData;
import com.simpleshop.order.dto.ShipLogisticsRequest;

/**
 * 卖家订单操作：按序推进与取消。卖家可以操作全部订单。
 *
 * <p>卖家没有「交易完成」：成交只由买家确认收货推进，这条边不在卖家这一侧。
 */
public interface SellerOrderService {

    /** 已支付 → 已确认。待支付阶段不能确认，必须等付款。 */
    OrderStatusData confirm(String orderId);

    /** 已确认 → 备货完成；只是流程标记，不校验库存。 */
    OrderStatusData prepare(String orderId);

    /** 备货完成 → 已发货；必须带承运商与单号，此后买家不可取消。 */
    OrderStatusData ship(String orderId, ShipLogisticsRequest request);

    /** 交易完成前整单取消；已支付订单同时退款。已取消的订单重复取消按成功返回（防抖）。 */
    OrderStatusData cancel(String orderId);
}
