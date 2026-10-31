package com.simpleshop.intention.enums;

/**
 * 交易失败裁决方式。
 *
 * <p>VOIDED：作废，意向进入失败终态；REQUEUED：重新排队到队尾，下单时间不变。
 */
public enum TradeDisposal {
    VOIDED,
    REQUEUED
}
