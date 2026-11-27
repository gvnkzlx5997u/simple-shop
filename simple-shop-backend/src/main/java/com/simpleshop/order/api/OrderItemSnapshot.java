package com.simpleshop.order.api;

import java.math.BigDecimal;

/**
 * 订单项的金额与数量快照：售后计算退款金额只需要这两项。
 *
 * <p>售后拿不到订单项实体，也不该拿：它只需要「这行值多少钱、几件」，
 * 以及一个能加锁的锚点。
 */
public record OrderItemSnapshot(String orderItemId, BigDecimal unitPrice, int quantity) {

    /** 该行的应退金额：单价 × 数量，两位小数。 */
    public BigDecimal amount() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2);
    }
}
