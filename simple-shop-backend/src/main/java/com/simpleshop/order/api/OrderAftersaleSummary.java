package com.simpleshop.order.api;

import java.math.BigDecimal;

/**
 * 一笔订单的售后汇总：条数 + 已退金额。
 *
 * <p>订单详情要把「已退款」显示成一个数，而退款有两个来源：取消退款（支付包流水）与
 * 售后退款（售后单）。订单只认识这个汇总接口，不认识售后单长什么样。
 */
public record OrderAftersaleSummary(long count, BigDecimal refundedAmount) {

    public OrderAftersaleSummary {
        if (refundedAmount == null) {
            refundedAmount = BigDecimal.ZERO.setScale(2);
        }
    }

    /** 还没有售后时用：零条、零元。 */
    public static OrderAftersaleSummary empty() {
        return new OrderAftersaleSummary(0, BigDecimal.ZERO.setScale(2));
    }
}
