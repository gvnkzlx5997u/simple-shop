package com.simpleshop.aftersale.enums;

/**
 * 售后处理结果。只在售后单完成时写入，三选一。
 *
 * <p>前两项是退款类结果：系统按订单项「单价 × 数量」写退款金额，原路退回，
 * 并且占用该订单项唯一的一条退款额度。换货不产生退款，也不占额度——
 * 换货之后仍可对同一订单项再申请退款，是否允许由卖家线下判断。
 */
public enum AftersaleResult {

    /** 退货并退款：买家退货，卖家确认收到后完成。 */
    RETURN_AND_REFUND,

    /** 仅退款：不退货，只退该订单项金额。 */
    REFUND_ONLY,

    /** 换货：卖家线下补发，系统不记新物流、不产生退款。 */
    EXCHANGE;

    public boolean producesRefund() {
        return this == RETURN_AND_REFUND || this == REFUND_ONLY;
    }
}
