package com.simpleshop.payment.api;

/**
 * 支付渠道。订单要记实际使用的渠道，退款按原路退回，所以这个枚举是订单与支付包共享的契约。
 *
 * <p>四个渠道本期都由 Mock 网关承载；接真实渠道时按渠道找适配器，枚举本身不需要变。
 * 数据库不对渠道做 CHECK：加渠道是业务配置，不该变成一次表结构迁移，合法性由入口校验。
 */
public enum PaymentChannel {

    BANK_CARD,
    CREDIT_CARD,
    ALIPAY,
    WECHAT
}
