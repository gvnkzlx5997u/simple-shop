package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.enums.OrderStatus;

/**
 * 发货后改物流：只改承运商、单号与备注，状态不动；这是高频修正动作，不写审计。
 *
 * <p>允许的范围是「已发货且未取消」：待支付、已支付这些还没有物流可改，
 * 已取消的订单也不该再动。
 */
class SellerLogisticsApiTest extends PaymentTestSupport {

    @Test
    @DisplayName("发货后改物流：三字段更新、状态不变，买家详情看到最新值")
    void updatesLogisticsAfterShipping() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        pay(buyerToken, order.getId(), "ALIPAY");
        ship(order.getId());

        String sellerToken = bearer(loginSeller("Abcd@1234"));
        MvcResult result = mockMvc.perform(put("/api/seller/orders/{id}/logistics", order.getId())
                        .header("Authorization", sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "carrier", "京东物流",
                                "tracking_no", "JD1\nJD2",
                                "logistics_remark", "分两个包裹"))))
                .andExpect(status().isOk())
                .andReturn();
        assertApiCode(result, 0);

        Order updated = reload(order);
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(updated.getCarrier()).isEqualTo("京东物流");
        assertThat(updated.getTrackingNo()).contains("\n");
        assertThat(updated.getLogisticsRemark()).isEqualTo("分两个包裹");

        JsonNode detail = dataOf(mockMvc.perform(get("/api/buyer/orders/{id}", order.getId())
                        .header("Authorization", buyerToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(detail.path("carrier").asText()).isEqualTo("京东物流");
        assertThat(detail.path("tracking_no").asText()).contains("JD2");
        assertThat(detail.path("logistics_remark").asText()).isEqualTo("分两个包裹");
    }

    @Test
    @DisplayName("还没发货就改物流：82002")
    void rejectsLogisticsBeforeShipping() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String sellerToken = bearer(loginSeller("Abcd@1234"));

        assertApiCode(updateLogistics(sellerToken, order.getId()), 82002);
        assertThat(reload(order).getCarrier()).isNull();
    }

    @Test
    @DisplayName("已取消订单不能再改物流：82002")
    void rejectsLogisticsOnCanceledOrder() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        pay(buyerToken, order.getId(), "ALIPAY");
        ship(order.getId());
        String sellerToken = bearer(loginSeller("Abcd@1234"));
        mockMvc.perform(post("/api/seller/orders/{id}/cancel", order.getId())
                        .header("Authorization", sellerToken))
                .andExpect(status().isOk());

        assertApiCode(updateLogistics(sellerToken, order.getId()), 82002);
    }

    @Test
    @DisplayName("买家令牌不能改物流")
    void rejectsBuyerToken() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        pay(buyerToken, order.getId(), "ALIPAY");
        ship(order.getId());

        mockMvc.perform(put("/api/seller/orders/{id}/logistics", order.getId())
                        .header("Authorization", buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("carrier", "顺丰速运", "tracking_no", "SF1"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("缺单号按参数校验拒绝，不是业务码")
    void rejectsMissingTrackingNoAsParameterError() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        pay(buyerToken, order.getId(), "ALIPAY");
        ship(order.getId());

        assertApiFieldError(mockMvc.perform(
                        put("/api/seller/orders/{id}/logistics", order.getId())
                                .header("Authorization", bearer(loginSeller("Abcd@1234")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("carrier", "顺丰速运", "tracking_no", " "))))
                .andReturn(), "tracking_no");
    }

    private MvcResult updateLogistics(String sellerToken, String orderId) throws Exception {
        return mockMvc.perform(put("/api/seller/orders/{id}/logistics", orderId)
                        .header("Authorization", sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("carrier", "京东物流", "tracking_no", "JD9"))))
                .andReturn();
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
