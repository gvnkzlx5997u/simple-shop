package com.simpleshop.aftersale.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.aftersale.enums.AftersaleResult;
import com.simpleshop.aftersale.enums.AftersaleStatus;
import com.simpleshop.aftersale.repository.AftersaleRepository;
import com.simpleshop.order.api.OrderAftersalePort;
import com.simpleshop.order.api.OrderAftersaleSummary;

/**
 * 售后汇总端口实现：给订单详情提供「售后条数」与「售后退款合计」。
 *
 * <p>订单详情要显示一个「已退款」，而退款有两个来源：取消退款（支付包流水）与售后退款（售后单）。
 * 这里只负责后者，前者由订单侧用 `PaymentPort.refundedTotal` 取，两处相加得到展示值——
 * 任何一张表都不存这个派生的总数。
 *
 * <p>退款合计只统计「已完成 + 退款类结果」：待处理的单还没退，换货与已取消根本不退。
 */
@Component
public class AftersaleSummaryAdapter implements OrderAftersalePort {

    private static final List<AftersaleResult> REFUND_RESULTS =
            List.of(AftersaleResult.RETURN_AND_REFUND, AftersaleResult.REFUND_ONLY);

    private final AftersaleRepository aftersales;

    public AftersaleSummaryAdapter(AftersaleRepository aftersales) {
        this.aftersales = aftersales;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderAftersaleSummary summaryOf(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return OrderAftersaleSummary.empty();
        }
        long count = aftersales.countByOrderId(orderId.trim());
        if (count == 0) {
            return OrderAftersaleSummary.empty();
        }
        BigDecimal refunded = aftersales.sumRefundByOrderId(
                orderId.trim(), AftersaleStatus.COMPLETED, REFUND_RESULTS);
        return new OrderAftersaleSummary(count, refunded);
    }
}
