package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.payment.api.PaymentChannel;

/**
 * 渠道失败分支：用配置把某个渠道置为失败，验证「失败不落订单字段、可换渠道重试」。
 *
 * <p>配置故意写小写，顺带验证渠道名解析与大小写无关。
 */
@TestPropertySource(properties = "simple-shop.payment.mock.fail-channels=alipay")
class BuyerPaymentFailureApiTest extends PaymentTestSupport {

    @Test
    @DisplayName("渠道失败返回 83003：订单仍待支付、支付字段为空，换渠道重试可成功")
    void failedPaymentKeepsOrderUnpaidAndIsRetryable() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        assertApiCode(pay(token, order.getId(), "ALIPAY"), 83003);

        Order failed = reload(order);
        assertThat(failed.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(failed.getPaymentChannel()).isNull();
        assertThat(failed.getPaidAt()).isNull();

        assertApiCode(pay(token, order.getId(), "WECHAT"), 0);

        Order paid = reload(order);
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.getPaymentChannel()).isEqualTo(PaymentChannel.WECHAT);
        assertThat(paid.getPaidAt()).isNotNull();
    }
}
