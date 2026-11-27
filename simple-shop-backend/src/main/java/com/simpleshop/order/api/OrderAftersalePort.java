package com.simpleshop.order.api;

/**
 * 售后汇总端口：声明在 order.api，由 aftersale 实现。
 *
 * <p>方向是反的：订单详情要显示「已退款」和售后条数，但不能依赖 aftersale 包，
 * 否则 order → aftersale 与 aftersale → order.api 会形成环。所以端口放在订单这一侧，
 * 由售后实现（与阶段三「商品问订单有没有未完成订单」同一手法）。
 *
 * <p>本期售后未落地，调用方用 {@link OrderAftersaleSummary#empty()} 兜底；
 * T5 提供实现后自动接上，订单侧代码不用改。
 */
public interface OrderAftersalePort {

    /** 该订单的售后条数与退款合计；没有售后返回 {@link OrderAftersaleSummary#empty()}。 */
    OrderAftersaleSummary summaryOf(String orderId);
}
