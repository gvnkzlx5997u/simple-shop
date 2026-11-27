package com.simpleshop.order.service;

/**
 * 超时取消：把过了支付时限仍未付款的订单置为已取消。
 *
 * <p>不改库存、不退款——待支付订单本来就没扣过库存、没付过钱。与取消相关的退款逻辑
 * 只对已支付订单生效，这里刻意不共用，避免以后有人顺手在这里加一次退款。
 */
public interface OrderTimeoutService {

    /**
     * 取消一笔到期未支付的订单。
     *
     * @return 真的取消了返回 true；订单不存在、已不是待支付、或还没到期都返回 false
     */
    boolean cancelExpired(String orderId);
}
