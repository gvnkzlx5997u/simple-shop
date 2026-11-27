package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.payment.entity.PaymentRefund;
import com.simpleshop.payment.repository.PaymentRefundRepository;

/**
 * 取消与退款：待支付取消不退款，已支付取消全额原路退，退款流水只落一条。
 *
 * <p>取消是整单的、不改库存；这里只验证订单侧的行为，库存不变由确认收货与取消共用的口径保证。
 */
class OrderCancelRefundApiTest extends PaymentTestSupport {

    @Autowired
    private PaymentRefundRepository refunds;

    @Test
    @DisplayName("待支付取消：订单已取消，不产生退款流水")
    void cancelingUnpaidOrderRefundsNothing() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        assertApiCode(cancelByBuyer(token, order.getId()), 0);

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(refunds.findByOrderIdOrderByRefundedAtDesc(order.getId())).isEmpty();
        assertThat(refundedAmountOf(token, order.getId())).isZero();
    }

    @Test
    @DisplayName("已支付取消：全额原路退款，流水一条且金额等于订单总额")
    void cancelingPaidOrderRefundsInFull() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        pay(token, order.getId(), "ALIPAY");

        assertApiCode(cancelByBuyer(token, order.getId()), 0);

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.CANCELED);
        var ledger = refunds.findByOrderIdOrderByRefundedAtDesc(order.getId());
        assertThat(ledger).hasSize(1);
        assertThat(ledger.get(0).getAmount()).isEqualByComparingTo(order.getTotalAmount());
        assertThat(ledger.get(0).getChannel()).isEqualTo(order.getPaymentChannel());
        assertThat(ledger.get(0).getReason()).isEqualTo(PaymentRefund.REASON_ORDER_CANCEL);
        assertThat(refundedAmountOf(token, order.getId()))
                .isEqualByComparingTo(order.getTotalAmount());
    }

    @Test
    @DisplayName("已发货后买家不能取消；卖家取消已支付订单同样退款")
    void shippedOrderCanOnlyBeCanceledBySeller() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        pay(buyerToken, order.getId(), "WECHAT");
        ship(order.getId());

        assertApiCode(cancelByBuyer(buyerToken, order.getId()), 60003);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.SHIPPED);

        String sellerToken = bearer(loginSeller("Abcd@1234"));
        assertApiCode(mockMvc.perform(post("/api/seller/orders/{id}/cancel", order.getId())
                        .header("Authorization", sellerToken))
                .andReturn(), 0);

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(refunds.findByOrderIdOrderByRefundedAtDesc(order.getId())).hasSize(1);
    }

    @Test
    @DisplayName("重复取消：返回成功且退款流水仍只有一条")
    void repeatedCancelRefundsOnce() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        pay(token, order.getId(), "CREDIT_CARD");

        assertApiCode(cancelByBuyer(token, order.getId()), 0);
        assertApiCode(cancelByBuyer(token, order.getId()), 0);

        assertThat(refunds.findByOrderIdOrderByRefundedAtDesc(order.getId())).hasSize(1);
        assertThat(refundedAmountOf(token, order.getId()))
                .isEqualByComparingTo(order.getTotalAmount());
    }

    private MvcResult cancelByBuyer(String token, String orderId) throws Exception {
        return mockMvc.perform(post("/api/buyer/orders/{id}/cancel", orderId)
                        .header("Authorization", token))
                .andReturn();
    }

    private BigDecimal refundedAmountOf(String token, String orderId) throws Exception {
        JsonNode detail = dataOf(mockMvc.perform(get("/api/buyer/orders/{id}", orderId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn());
        return detail.path("refunded_amount").decimalValue();
    }

    /** 支付 → 卖家确认 → 备货完成 → 发货。 */
    private void ship(String orderId) throws Exception {
        String sellerToken = bearer(loginSeller("Abcd@1234"));
        mockMvc.perform(post("/api/seller/orders/{id}/confirm", orderId)
                .header("Authorization", sellerToken)).andExpect(status().isOk());
        mockMvc.perform(post("/api/seller/orders/{id}/prepare", orderId)
                .header("Authorization", sellerToken)).andExpect(status().isOk());
        mockMvc.perform(post("/api/seller/orders/{id}/ship", orderId)
                        .header("Authorization", sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("carrier", "顺丰速运", "tracking_no", "SF123456"))))
                .andExpect(status().isOk());
    }
}
