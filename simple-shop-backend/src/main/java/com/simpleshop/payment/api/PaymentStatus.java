package com.simpleshop.payment.api;

/**
 * 一次支付调用的结果状态。
 *
 * <p>本期 Mock 只会产出 {@link #SUCCEEDED} 与 {@link #FAILED}；{@link #PENDING} 是为真实渠道
 * （跳转收银台、等异步通知）预留的第三个分支。订单侧现在就把 PENDING 处理好：
 * 保持待支付、把跳转地址回给前端，将来接网关时不用重排接口。
 */
public enum PaymentStatus {

    /** 已成功，渠道流水号与支付时间可用。 */
    SUCCEEDED,

    /** 已受理、结果未定：真实渠道重定向后用。本期不会出现。 */
    PENDING,

    /** 渠道拒绝或失败：订单停在待支付，可重试。 */
    FAILED
}
