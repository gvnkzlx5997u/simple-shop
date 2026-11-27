package com.simpleshop.order.support;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.simpleshop.common.config.AppProperties;
import com.simpleshop.common.time.DatabaseTimeProvider;
import com.simpleshop.order.entity.Order;

/**
 * 支付时限：下单时刻 + 配置的时长。
 *
 * <p>时限是订单侧的概念，支付包不知道也不该知道。这里单独成 Bean，是因为三处要共用一个口径：
 * 支付入口判断「是否已超时」、订单详情下发倒计时、超时任务扫描到期订单。各处自己算一遍，
 * 迟早出现三个不同的截止时间。
 */
@Component
public class PaymentDeadline {

    private final Duration timeout;

    public PaymentDeadline(AppProperties properties) {
        this.timeout = properties.getOrder().getPaymentTimeout();
    }

    /** 订单必须支付到什么时候；只有待支付订单才有意义。 */
    public LocalDateTime of(Order order) {
        return order.getCreateAt().plus(timeout);
    }

    /**
     * 到期分界线：创建时间早于它的待支付订单就算超时。给扫描任务做查询条件用，
     * 不要拿它去比较单笔订单（单笔用 {@link #isExpired(Order)}）。
     */
    public LocalDateTime expiredBefore() {
        return DatabaseTimeProvider.utcNow().minus(timeout);
    }

    /** 到点即算超时（含边界）：上限是「24 小时内付」，第 24 小时整点不再接受支付。 */
    public boolean isExpired(Order order) {
        return !DatabaseTimeProvider.utcNow().isBefore(of(order));
    }
}
