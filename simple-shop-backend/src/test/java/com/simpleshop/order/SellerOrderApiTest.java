package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.testing.ApiTestBase;

class SellerOrderApiTest extends ApiTestBase {

    private static final String SELLER_PASSWORD = "Abcd@1234";
    private static final int ORDER_NOT_FOUND = 60001;
    private static final int ORDER_STATUS_INVALID = 60002;
    private static final int ORDER_NOT_CANCELABLE = 60003;

    @Test
    @DisplayName("卖家按序推进：确认 → 备货完成 → 已发货，每次一格")
    void advancesOneStepAtATime() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        Order order = pendingOrder();

        MvcResult confirmed = advance(token, order, "confirm");
        assertApiCode(confirmed, 0);
        assertThat(dataOf(confirmed).path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(statusOf(order)).isEqualTo("CONFIRMED");

        MvcResult prepared = advance(token, order, "prepare");
        assertApiCode(prepared, 0);
        assertThat(dataOf(prepared).path("status").asText()).isEqualTo("PREPARED");
        assertThat(statusOf(order)).isEqualTo("PREPARED");

        MvcResult shipped = advance(token, order, "ship");
        assertApiCode(shipped, 0);
        assertThat(dataOf(shipped).path("status").asText()).isEqualTo("SHIPPED");
        assertThat(statusOf(order)).isEqualTo("SHIPPED");
        assertThat(dataOf(shipped).path("order_no").asText()).isEqualTo(order.getOrderNo());
    }

    @Test
    @DisplayName("跳级与重复推进都返回状态不允许，订单停在原状态")
    void rejectsSkippedOrRepeatedAdvance() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        Order order = pendingOrder();

        assertApiStatusAndCode(advance(token, order, "prepare"), 200, ORDER_STATUS_INVALID);
        assertApiStatusAndCode(advance(token, order, "ship"), 200, ORDER_STATUS_INVALID);
        assertThat(statusOf(order)).isEqualTo("PENDING_CONFIRM");

        assertApiCode(advance(token, order, "confirm"), 0);
        assertApiStatusAndCode(advance(token, order, "confirm"), 200, ORDER_STATUS_INVALID);
        assertApiStatusAndCode(advance(token, order, "ship"), 200, ORDER_STATUS_INVALID);
        assertThat(statusOf(order)).isEqualTo("CONFIRMED");

        assertApiCode(advance(token, order, "prepare"), 0);
        assertApiCode(advance(token, order, "ship"), 0);
        assertApiStatusAndCode(advance(token, order, "prepare"), 200, ORDER_STATUS_INVALID);
        assertThat(statusOf(order)).isEqualTo("SHIPPED");
    }

    @Test
    @DisplayName("卖家可在已发货后取消；交易完成后不可取消；终态不可再推进")
    void sellerCancelsBeforeCompletion() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        Order shipped = shippedOrder();

        MvcResult canceled = advance(token, shipped, "cancel");
        assertApiCode(canceled, 0);
        assertThat(dataOf(canceled).path("status").asText()).isEqualTo("CANCELED");
        assertThat(statusOf(shipped)).isEqualTo("CANCELED");

        assertApiStatusAndCode(advance(token, shipped, "confirm"), 200, ORDER_STATUS_INVALID);

        Order completed = pendingOrder();
        markStatus(completed, OrderStatus.COMPLETED);
        assertApiStatusAndCode(advance(token, completed, "cancel"), 200, ORDER_NOT_CANCELABLE);
        assertThat(statusOf(completed)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("订单不存在返回 404/60001")
    void rejectsUnknownOrder() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/seller/orders/O-NOT-EXISTS/confirm")
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiStatusAndCode(result, 404, ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("卖家接口不接受买家令牌")
    void requiresSellerSession() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = pendingOrder();
        String buyerToken = loginBuyer(buyer.getUsername(), "Buyer@1234");

        MvcResult result = mockMvc.perform(post("/api/seller/orders/" + order.getId() + "/confirm")
                        .header("Authorization", bearer(buyerToken)))
                .andReturn();
        assertApiStatusAndCode(result, 401, 10002);
    }

    private MvcResult advance(String token, Order order, String action) throws Exception {
        return mockMvc.perform(post("/api/seller/orders/" + order.getId() + "/" + action)
                        .header("Authorization", bearer(token)))
                .andReturn();
    }

    private Order pendingOrder() {
        BuyerAccount buyer = createBuyer();
        Goods goods = createGoods(createCategory());
        return createOrder(buyer, goods, 1);
    }

    private Order shippedOrder() {
        Order order = pendingOrder();
        markStatus(order, OrderStatus.SHIPPED);
        return order;
    }

    /**
     * 直接改库准备中间态夹具。改完必须清持久化上下文：否则服务层加锁读到的是
     * 上下文里那份旧实体，状态守卫会判错。
     */
    private void markStatus(Order order, OrderStatus status) {
        jdbcTemplate.update("update simpleshop_order set status = ? where id = ?",
                status.name(), order.getId());
        flushAndClear();
    }

    private String statusOf(Order order) {
        return jdbcTemplate.queryForObject(
                "select status from simpleshop_order where id = ?", String.class, order.getId());
    }
}
