package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * 卖家推进：确认 → 备货完成 → 发货，每次一格；确认前必须有支付。
 *
 * <p>阶段四卖家不再有「完成订单」：成交由买家确认收货推进，所以本类最后一个合法动作是发货。
 */
class SellerOrderApiTest extends PaymentTestSupport {

    private static final int STATUS_INVALID = 60002;
    private static final int NOT_CANCELABLE = 60003;

    @Test
    @DisplayName("支付后逐格推进：确认 → 备货完成 → 已发货，发货必须带承运商与单号")
    void advancesOneStepAtATime() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        pay(buyerToken, order.getId(), "ALIPAY");
        String seller = sellerToken();

        assertThat(dataOf(advance(seller, order.getId(), "confirm")).path("status").asText())
                .isEqualTo("CONFIRMED");
        assertThat(dataOf(advance(seller, order.getId(), "prepare")).path("status").asText())
                .isEqualTo("PREPARED");

        MvcResult shipped = mockMvc.perform(post("/api/seller/orders/{id}/ship", order.getId())
                        .header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("carrier", "顺丰速运", "tracking_no", "SF123456"))))
                .andExpect(status().isOk())
                .andReturn();
        assertApiCode(shipped, 0);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(reload(order).getCarrier()).isEqualTo("顺丰速运");
        assertThat(reload(order).getTrackingNo()).isEqualTo("SF123456");
    }

    @Test
    @DisplayName("待支付不能确认；跳级与重复推进都返回状态不允许")
    void rejectsAdvanceBeforePaymentAndSkippedSteps() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String seller = sellerToken();

        assertApiCode(advance(seller, order.getId(), "confirm"), STATUS_INVALID);
        assertApiCode(advance(seller, order.getId(), "prepare"), STATUS_INVALID);
        assertApiCode(ship(seller, order.getId()), STATUS_INVALID);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        pay(buyerToken, order.getId(), "ALIPAY");
        assertApiCode(advance(seller, order.getId(), "prepare"), STATUS_INVALID);

        assertApiCode(advance(seller, order.getId(), "confirm"), 0);
        assertApiCode(advance(seller, order.getId(), "confirm"), STATUS_INVALID);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("卖家可取消已发货订单（已支付则退款）；交易完成后不可取消")
    void sellerCancelsBeforeCompletion() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        pay(buyerToken, order.getId(), "ALIPAY");
        String seller = sellerToken();
        advance(seller, order.getId(), "confirm");
        advance(seller, order.getId(), "prepare");
        ship(seller, order.getId());

        MvcResult canceled = mockMvc.perform(post("/api/seller/orders/{id}/cancel", order.getId())
                        .header("Authorization", seller))
                .andExpect(status().isOk())
                .andReturn();
        assertApiCode(canceled, 0);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.CANCELED);

        // 另一单走到交易完成，卖家再取消应被拒
        Order completed = placeOrder(buyer);
        pay(buyerToken, completed.getId(), "WECHAT");
        advance(seller, completed.getId(), "confirm");
        advance(seller, completed.getId(), "prepare");
        ship(seller, completed.getId());
        mockMvc.perform(post("/api/buyer/orders/{id}/confirm-receipt", completed.getId())
                        .header("Authorization", buyerToken))
                .andExpect(status().isOk());

        assertApiCode(mockMvc.perform(post("/api/seller/orders/{id}/cancel", completed.getId())
                        .header("Authorization", seller))
                .andReturn(), NOT_CANCELABLE);
        assertThat(reload(completed).getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("订单不存在返回 404/60001；买家令牌不能调卖家接口")
    void rejectsUnknownOrderAndBuyerToken() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer);
        String seller = sellerToken();

        assertApiStatusAndCode(advance(seller, "O-NOT-EXISTS", "confirm"), 404, 60001);

        assertApiStatusAndCode(mockMvc.perform(
                        post("/api/seller/orders/{id}/confirm", order.getId())
                                .header("Authorization", bearer(loginBuyer(
                                        buyer.getUsername(), PASSWORD))))
                .andReturn(), 401, 10002);
    }

    private String sellerToken() throws Exception {
        return bearer(loginSeller("Abcd@1234"));
    }

    private MvcResult advance(String seller, String orderId, String action) throws Exception {
        return mockMvc.perform(post("/api/seller/orders/{id}/{action}", orderId, action)
                        .header("Authorization", seller))
                .andReturn();
    }

    private MvcResult ship(String seller, String orderId) throws Exception {
        return mockMvc.perform(post("/api/seller/orders/{id}/ship", orderId)
                        .header("Authorization", seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("carrier", "顺丰速运", "tracking_no", "SF1"))))
                .andReturn();
    }
}
