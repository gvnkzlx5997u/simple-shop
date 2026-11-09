package com.simpleshop.order.enums;

import java.util.Set;

/**
 * 订单状态。整单流转，只能按序前进一格，不可跳级或回退。
 *
 * <p>订单项没有状态：明细的有效性由整单状态派生，因此状态判断只读订单头。
 */
public enum OrderStatus {

    /** 待确认：买家已提交，等待卖家确认。 */
    PENDING_CONFIRM,

    /** 已确认：卖家已接单。 */
    CONFIRMED,

    /** 备货完成：只是流程状态，不代表库存充足。 */
    PREPARED,

    /** 已发货：买家取消的边界，进入本状态后买家不可取消。 */
    SHIPPED,

    /** 交易完成：扣减库存成功后才进入，终态。 */
    COMPLETED,

    /** 已取消：整单终止，终态；不区分取消方。 */
    CANCELED;

    /** 终态没有任何出边，买卖双方都不能再推进或取消。 */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELED;
    }

    /**
     * 未完成订单 = 待确认至已发货四态。商品手动下架与手动改库存为 0 时据此阻断。
     */
    public boolean isUnfinished() {
        return !isTerminal();
    }

    /** 未完成四态。用于按商品判断是否存在未完成订单，避免调用方各自拼状态集合。 */
    public static Set<OrderStatus> unfinished() {
        return Set.of(PENDING_CONFIRM, CONFIRMED, PREPARED, SHIPPED);
    }
}
