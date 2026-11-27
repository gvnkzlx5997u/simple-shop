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
     * 某订单已退金额，只含取消退款。售后退款记在售后单里，由 aftersale 自己聚合，
     * 两处相加才是订单详情要展示的「已退款」。
     */
    BigDecimal refundedTotal(String orderId);
}
