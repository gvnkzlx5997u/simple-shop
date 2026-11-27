package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.payment.api.PaymentChannel;

/**
 * 买家支付：Mock 网关下四个渠道都能付通，失败与拒绝各有明确业务码。
 *
 * <p>失败注入（fail-channels）放在另一个类里，因为它需要独立的配置上下文；
 * 这里只验证成功路径与「不该付的时候不许付」。
 */
class BuyerPaymentApiTest extends PaymentTestSupport {

    @Test
    @DisplayName("四个渠道都能支付成功：订单转已支付，渠道与支付时间落库")
    void everyChannelPaysSuccessfully() throws Exception {
        for (PaymentChannel channel : PaymentChannel.values()) {
            BuyerAccount buyer = createBuyer();
            Order order = placeOrder(buyer);
            String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

            JsonNode data = dataOf(pay(token, order.getId(), channel.name()));

            assertThat(data.path("status").asText()).isEqualTo("PAID");
            assertThat(data.path("payment_channel").asText()).isEqualTo(channel.name());
            assertThat(data.path("paid_at").asText()).endsWith("Z");
            assertThat(data.path("redirect_url").isNull()).isTrue();

            Order paid = reload(order);
            assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(paid.getPaymentChannel()).isEqualTo(channel);
            assertThat(paid.getPaidAt()).isNotNull();
        }
    }

    @Test
    @DisplayName("重复支付被拒绝，支付渠道与时间不变")
    void rejectsPayingTwice() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        pay(token, order.getId(), "ALIPAY");
        LocalDateTime paidAt = reload(order).getPaidAt();

        assertApiCode(pay(token, order.getId(), "WECHAT"), 83002);
        Order after = reload(order);
        assertThat(after.getPaidAt()).isEqualTo(paidAt);
        assertThat(after.getPaymentChannel()).isEqualTo(PaymentChannel.ALIPAY);
    }

    @Test
    @DisplayName("非法渠道返回 83001，订单仍是待支付")
    void rejectsUnknownChannel() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        assertApiCode(pay(token, order.getId(), "CASH"), 83001);

        Order untouched = reload(order);
        assertThat(untouched.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(untouched.getPaymentChannel()).isNull();
        assertThat(untouched.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("超过支付时限返回 83002，订单仍是待支付")
    void rejectsExpiredPayment() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        LocalDateTime expired = LocalDateTime.now(ZoneOffset.UTC).minusHours(25);
        jdbcTemplate.update(
                "update simpleshop_order set create_at = ?, update_at = ? where id = ?",
                expired, expired, order.getId());
        // 直改库绕过了持久化上下文，必须清掉一级缓存，否则加锁查询拿到的是旧 create_at。
        flushAndClear();

        assertApiCode(pay(token, order.getId(), "ALIPAY"), 83002);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("已取消的订单不能再支付")
    void rejectsCanceledOrder() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        mockMvc.perform(post("/api/buyer/orders/{id}/cancel", order.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk());

        assertApiCode(pay(token, order.getId(), "ALIPAY"), 83002);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("不能支付别人的订单：按订单不存在处理")
    void rejectsPayingOthersOrder() throws Exception {
        BuyerAccount owner = createBuyer();
        Order order = placeOrder(owner);
        BuyerAccount other = createBuyer();
        String otherToken = bearer(loginBuyer(other.getUsername(), PASSWORD));

        // 他人订单按「不存在」处理，所以是 404 + 60001，不是 200。
        assertApiStatusAndCode(pay(otherToken, order.getId(), "ALIPAY"), 404, 60001);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }
}
