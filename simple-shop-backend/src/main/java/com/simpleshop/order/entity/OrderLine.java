package com.simpleshop.order.entity;

import java.math.BigDecimal;

/**
 * 一条待落库的订单明细：商品引用与下单快照。
 *
 * <p>由下单服务按商品当前状态组装后交给 {@link Order#place}，订单只在构造时接收一次，
 * 之后不再修改明细，避免出现「先建空订单再补行」的半成品。
 */
public record OrderLine(
        String goodsId,
        String goodsName,
        BigDecimal unitPrice,
        int quantity) {
}
