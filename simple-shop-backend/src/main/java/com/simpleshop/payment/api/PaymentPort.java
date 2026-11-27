package com.simpleshop.payment.api;

import java.math.BigDecimal;

/**
 * 支付端口：订单侧唯一可见的支付能力。
 *
 * <p>三个方法都只收参数、不收实体，支付包不读订单表。凡涉及订单状态、库存、
 * 金额是否该退的判断都在 order 侧；这里只回答「这次渠道调用成没成」。
 *
 * <p>为什么退款和支付放在同一个端口：本期只有一套渠道适配，拆成两个端口只会让
 * 订单侧多注入一个依赖；接真实渠道后如果支付与退款的接入方式差异很大，再拆不迟。
 */
public interface PaymentPort {

    /**
     * 发起支付。渠道失败以 {@link PaymentStatus#FAILED} 返回，不抛业务异常——
     * 「这次没刷成功」是可重试的正常分支。
     */
    PaymentResult charge(PaymentRequest request);

    /**
     * 原路退款。同一订单同一原因重复调用时返回已有流水的回执（幂等），
     * 不再调用渠道；金额与渠道由调用方给出。
     */
    RefundReceipt refund(RefundRequest request);

    /**
     * 取消已支付订单的整单退款：金额是订单总额，渠道是原支付渠道，原因固定为 ORDER_CANCEL。
     *
     * <p>单独给一个方法而不是让订单侧自己拼 reason：退款原因是支付包的记账口径，
     * 调用方只需要表达「这单取消了」。同一订单重复调用返回已有回执，不会重复退。
     */
    RefundReceipt refundForCancel(
            String orderId, String orderNo, BigDecimal amount, PaymentChannel channel);

    /**
     * 某订单已退金额，只含取消退款。售后退款记在售后单里，由 aftersale 自己聚合，
     * 两处相加才是订单详情要展示的「已退款」。
     */
    BigDecimal refundedTotal(String orderId);
}
