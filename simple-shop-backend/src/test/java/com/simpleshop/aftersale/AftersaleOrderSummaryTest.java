package com.simpleshop.aftersale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.enums.OrderStatus;

/**
 * 订单详情的售后聚合：条数与售后退款合计。
 *
 * <p>订单详情里的 `refunded_amount` 是两个来源的和——取消退款（支付包流水）与售后退款（售后单）。
 * 这里验证后者：只有「已完成 + 退款类结果」才计入金额，待处理与换货都只影响条数。
 */
class AftersaleOrderSummaryTest extends AftersaleTestSupport {

    @Test
    @DisplayName("售后仅退款：订单详情看到退款金额与条数，订单仍是交易完成")
    void refundIsAggregatedIntoOrderDetail() throws Exception {
        BuyerAccount buyer = createBuyer();
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        Order order = completedOrder(buyer);
        String aftersaleId = applyAndGetId(buyerToken, order, "仅退款", "商品有问题。");
        complete(aftersaleId, "REFUND_ONLY");

        JsonNode detail = orderDetail(buyerToken, order.getId());
        assertThat(detail.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(detail.path("aftersale_count").asLong()).isEqualTo(1);
        assertThat(detail.path("refunded_amount").decimalValue())
                .isEqualByComparingTo(order.getTotalAmount());
    }

    @Test
    @DisplayName("换货与待处理的售后只计入条数，不计入金额")
    void exchangeAndPendingCountButDoNotRefund() throws Exception {
        BuyerAccount buyer = createBuyer();
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        Order order = completedOrder(buyer);

        String pending = applyAndGetId(buyerToken, order, "待处理", "还没协商好。");
        JsonNode beforeDecision = orderDetail(buyerToken, order.getId());
        assertThat(beforeDecision.path("aftersale_count").asLong()).isEqualTo(1);
        assertThat(beforeDecision.path("refunded_amount").decimalValue()).isZero();

        complete(pending, "EXCHANGE");
        JsonNode afterExchange = orderDetail(buyerToken, order.getId());
        assertThat(afterExchange.path("aftersale_count").asLong()).isEqualTo(1);
        assertThat(afterExchange.path("refunded_amount").decimalValue()).isZero();
    }

    @Test
    @DisplayName("多条售后：条数按单累加，金额只算退款那一张")
    void multipleAftersalesAccumulate() throws Exception {
        BuyerAccount buyer = createBuyer();
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        Order order = completedOrder(buyer);

        String exchange = applyAndGetId(buyerToken, order, "先换货", "尺寸不合适。");
        complete(exchange, "EXCHANGE");
        String refund = applyAndGetId(buyerToken, order, "再退款", "换了还不满意。");
        complete(refund, "REFUND_ONLY");

        JsonNode detail = orderDetail(buyerToken, order.getId());
        assertThat(detail.path("aftersale_count").asLong()).isEqualTo(2);
        assertThat(detail.path("refunded_amount").decimalValue())
                .isEqualByComparingTo(order.getTotalAmount());
        assertThat(detail.path("status").asText()).isEqualTo(OrderStatus.COMPLETED.name());
    }

    @Test
    @DisplayName("没有售后时金额为零：不影响取消退款的统计口径")
    void noAftersaleMeansZero() throws Exception {
        BuyerAccount buyer = createBuyer();
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        Order order = completedOrder(buyer);

        JsonNode detail = orderDetail(buyerToken, order.getId());
        assertThat(detail.path("aftersale_count").asLong()).isZero();
        assertThat(detail.path("refunded_amount").decimalValue()).isZero();
    }

    private JsonNode orderDetail(String token, String orderId) throws Exception {
        return dataOf(mockMvc.perform(get("/api/buyer/orders/{id}", orderId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private String applyAndGetId(
            String buyerToken, Order order, String title, String description) throws Exception {
        var applied = apply(buyerToken, firstItemId(order), title, description, List.of());
        assertApiCode(applied, 0);
        return dataOf(applied).path("aftersale_id").asText();
    }

    private void complete(String aftersaleId, String result) throws Exception {
        assertApiCode(mockMvc.perform(
                        post("/api/seller/aftersales/{id}/complete", aftersaleId)
                                .header("Authorization", bearer(loginSeller("Abcd@1234")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("result", result))))
                .andReturn(), 0);
    }
}
