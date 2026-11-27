package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.order.service.OrderTimeoutService;
import com.simpleshop.order.service.OrderTimeoutSweeper;

/**
 * 超时取消：只碰「到期 + 待支付」，不退款、不改库存，重复执行幂等。
 *
 * <p>测试直接调用服务与扫描任务，不等定时器——否则用例只能靠 sleep，既慢又不稳。
 */
class OrderTimeoutTest extends PaymentTestSupport {

    @Autowired
    private OrderTimeoutService timeouts;

    @Autowired
    private OrderTimeoutSweeper sweeper;

    @Test
    @DisplayName("到期未支付：系统取消订单，不产生退款流水")
    void cancelsExpiredUnpaidOrder() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        expire(order, 25);

        assertThat(timeouts.cancelExpired(order.getId())).isTrue();

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getPaymentChannel()).isNull();
    }

    @Test
    @DisplayName("未到期不动；已取消的重复调用也返回 false")
    void leavesUnexpiredOrdersAlone() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);

        assertThat(timeouts.cancelExpired(order.getId())).isFalse();
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        expire(order, 25);
        assertThat(timeouts.cancelExpired(order.getId())).isTrue();
        assertThat(timeouts.cancelExpired(order.getId())).isFalse();
    }

    @Test
    @DisplayName("已支付订单不会被超时任务取消，也不退款")
    void neverTouchesPaidOrders() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        pay(token, order.getId(), "ALIPAY");
        expire(order, 25);

        assertThat(timeouts.cancelExpired(order.getId())).isFalse();
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("扫描任务只取消到期未支付的那几笔")
    void sweepCancelsOnlyExpiredUnpaidOrders() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order expiredFirst = placeOrder(buyer);
        Order expiredSecond = placeOrder(buyer);
        Order fresh = placeOrder(buyer);
        expire(expiredFirst, 30);
        expire(expiredSecond, 25);

        sweeper.sweep();

        assertThat(reload(expiredFirst).getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(reload(expiredSecond).getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(reload(fresh).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    /** 把下单时间挪到 hours 小时之前，模拟已过支付时限。 */
    private void expire(Order order, int hours) {
        LocalDateTime past = LocalDateTime.now(ZoneOffset.UTC).minusHours(hours);
        jdbcTemplate.update(
                "update simpleshop_order set create_at = ?, update_at = ? where id = ?",
                past, past, order.getId());
        flushAndClear();
    }
}
