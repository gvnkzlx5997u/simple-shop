package com.simpleshop.order.api;

import java.util.Optional;

/**
 * 交易只读端口：由 order 实现，供售后按订单项定位目标。
 *
 * <p>只读、只按订单项查：售后不需要改订单，也不需要知道订单有哪些字段之外的结构。
 * 订单项不存在、不属于该买家都返回空，由调用方统一按「订单项不存在」处理——
 * 分开报错等于告诉别人「这个订单项存在，但不是你的」。
 */
public interface OrderTradePort {

    Optional<AftersaleTarget> findAftersaleTarget(String buyerId, String orderItemId);

    /**
     * 锁住订单项行并返回金额快照，供售后「完成并退款」时串行化退款额度校验。
     *
     * <p>为什么锁订单项而不是售后单：同一个订单项可以有多张售后单，锁单张单子挡不住
     * 另一张同时退款；订单项不可变，拿它当锁不会影响订单的其它读写。
     */
    Optional<OrderItemSnapshot> lockOrderItem(String orderItemId);

    /** 按人类可读的订单号反查订单 ID，供卖家按订单号筛选售后单。 */
    Optional<String> findOrderIdByOrderNo(String orderNo);
}
