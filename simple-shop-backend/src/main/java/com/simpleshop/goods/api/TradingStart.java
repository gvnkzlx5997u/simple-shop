package com.simpleshop.goods.api;

/**
 * 进入交易的结果：由 intention 翻译成对外错误码。
 */
public enum TradingStart {
    STARTED,
    ALREADY_TRADING,
    NOT_ON_SALE
}
