package com.simpleshop.order.service;

import com.simpleshop.order.dto.OrderStatusData;

/**
 * 卖家订单操作：按序推进与取消。卖家可以操作全部订单。
 */
public interface SellerOrderService {

    /** 待确认 → 已确认。 */
    OrderStatusData confirm(String orderId);

    /** 已确认 → 备货完成；不校验库存。 */
    OrderStatusData prepare(String orderId);

    /** 备货完成 → 已发货；此后买家不可取消。 */
    OrderStatusData ship(String orderId);

    /**
     * 已发货 → 交易完成：逐条校验库存，全部充足才按数量扣减；
     * 任一不足则整单拒绝，订单停在已发货、不扣任何一行。
     */
    OrderStatusData complete(String orderId);

    /** 交易完成前整单取消；已取消的订单重复取消按成功返回（防抖）。 */
    OrderStatusData cancel(String orderId);
}
