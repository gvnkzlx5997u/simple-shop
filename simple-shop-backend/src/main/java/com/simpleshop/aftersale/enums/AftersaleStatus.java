package com.simpleshop.aftersale.enums;

/**
 * 售后单状态。只有三态：待卖家处理 → 已完成 / 已取消。
 *
 * <p>没有「已拒绝」：拒绝与取消是同一个动作，由卖家把待处理的单置为已取消；
 * 也没有「处理中」：协商在线下完成，系统只登记结果。
 */
public enum AftersaleStatus {

    /** 待处理：买家已提交，等卖家线下协商后登记结果。 */
    PENDING,

    /** 已完成：必须带处理结果；结果为退款类时已写退款金额与时间。 */
    COMPLETED,

    /** 已取消：卖家拒绝或撤销，不产生退款；终态，可再次申请新单。 */
    CANCELED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELED;
    }
}
