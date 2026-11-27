package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.order.entity.Order;

/**
 * 订单视图：详情与列表要带全收货快照、支付、物流与退款汇总，明细要能定位到订单项。
 *
 * <p>这里覆盖的是「读」的契约；写侧（支付、取消、发货）各有自己的用例。
 */
class OrderViewApiTest extends PaymentTestSupport {

    @Test
    @DisplayName("下单响应带支付截止时间；待支付详情有收货快照，支付与物流字段为空")
    void pendingOrderExposesSnapshotAndDeadline() throws Exception {
        BuyerAccount buyer = createBuyer();
        JsonNode placed = placeOrderData(buyer);

        assertThat(placed.path("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(placed.path("pay_deadline").asText()).endsWith("Z");
        String orderId = placed.path("order_id").asText();
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        JsonNode detail = detailOf(token, orderId);

        assertThat(detail.path("receiver_name").asText()).isEqualTo("张三");
        assertThat(detail.path("receiver_phone").asText()).isEqualTo("13800000000");
        assertThat(detail.path("receiver_address").asText()).isEqualTo("北京市朝阳区某路 1 号");
        assertThat(detail.path("pay_deadline").asText()).endsWith("Z");
        assertThat(detail.path("payment_channel").isNull()).isTrue();
        assertThat(detail.path("paid_at").isNull()).isTrue();
        assertThat(detail.path("carrier").isNull()).isTrue();
        assertThat(detail.path("tracking_no").isNull()).isTrue();
        assertThat(detail.path("refunded_amount").decimalValue()).isZero();
        assertThat(detail.path("aftersale_count").asLong()).isZero();
        assertThat(detail.path("item_count").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("订单项带 order_item_id，售后按订单项定位而不是按商品")
    void orderItemsCarryItemId() throws Exception {
        BuyerAccount buyer = createBuyer();
        String orderId = placeOrderData(buyer).path("order_id").asText();
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        MvcResult result = mockMvc.perform(get("/api/buyer/orders/{id}/items", orderId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = dataOf(result).path("items");
        assertThat(items).hasSize(1);
        String itemId = items.get(0).path("order_item_id").asText();
        assertThat(itemId).isNotBlank();
        assertThat(orderItems.findById(itemId)).isPresent();
        assertThat(items.get(0).path("amount").decimalValue())
                .isEqualByComparingTo(items.get(0).path("unit_price").decimalValue()
                        .multiply(java.math.BigDecimal.valueOf(
                                items.get(0).path("quantity").asInt())));
    }

    @Test
    @DisplayName("支付后详情回读渠道与支付时间，且不再给倒计时")
    void paidOrderExposesPaymentAndDropsDeadline() throws Exception {
        BuyerAccount buyer = createBuyer();
        String orderId = placeOrderData(buyer).path("order_id").asText();
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        pay(token, orderId, "ALIPAY");

        JsonNode detail = detailOf(token, orderId);
        assertThat(detail.path("status").asText()).isEqualTo("PAID");
        assertThat(detail.path("payment_channel").asText()).isEqualTo("ALIPAY");
        assertThat(detail.path("paid_at").asText()).endsWith("Z");
        assertThat(detail.path("pay_deadline").isNull()).isTrue();
        assertThat(detail.path("refunded_amount").decimalValue()).isZero();
    }

    @Test
    @DisplayName("卖家列表与详情共用同一形状：也能看到收货快照与状态")
    void sellerViewsShareTheSameShape() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String sellerToken = bearer(loginSeller("Abcd@1234"));

        JsonNode detail = dataOf(mockMvc.perform(
                        get("/api/seller/orders/{id}", order.getId())
                                .header("Authorization", sellerToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(detail.path("receiver_name").asText()).isEqualTo("张三");
        assertThat(detail.path("status").asText()).isEqualTo("PENDING_PAYMENT");

        JsonNode list = dataOf(mockMvc.perform(get("/api/seller/orders")
                        .header("Authorization", sellerToken)
                        .queryParam("status", "PENDING_PAYMENT"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(list.path("items")).isNotEmpty();
        assertThat(list.path("items").get(0).path("receiver_address").asText())
                .isEqualTo("北京市朝阳区某路 1 号");
    }

    @Test
    @DisplayName("非法状态筛选按参数错误拒绝")
    void rejectsUnknownStatusFilter() throws Exception {
        BuyerAccount buyer = createBuyer();
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        assertApiStatusAndCode(
                mockMvc.perform(get("/api/buyer/orders")
                                .header("Authorization", token)
                                .queryParam("status", "PENDING_CONFIRM"))
                        .andReturn(),
                400, 50002);
    }

    private JsonNode detailOf(String token, String orderId) throws Exception {
        return dataOf(mockMvc.perform(get("/api/buyer/orders/{id}", orderId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn());
    }
}
