package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.enums.OffShelfReason;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.entity.OrderLine;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.testing.ApiTestBase;

class OrderCompletionApiTest extends ApiTestBase {

    private static final String SELLER_PASSWORD = "Abcd@1234";
    private static final int ORDER_NOT_FOUND = 60001;
    private static final int ORDER_STATUS_INVALID = 60002;
    private static final int ORDER_STOCK_SHORTAGE = 60004;

    @Test
    @DisplayName("交易完成按明细数量扣库存，扣到 0 的商品置为已售下架")
    void deductsStockAndMarksSoldOut() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        CategoryL2 category = createCategory();
        Goods soldOut = createGoods(category);
        Goods remaining = createGoods(category);
        setStock(soldOut, 2);
        setStock(remaining, 5);

        Order order = orderOf(soldOut, 2, remaining, 1);
        markShipped(order);

        MvcResult result = complete(token, order);
        assertApiCode(result, 0);
        assertThat(dataOf(result).path("status").asText()).isEqualTo("COMPLETED");
        assertThat(statusOf(order)).isEqualTo("COMPLETED");

        assertThat(stockOf(soldOut)).isZero();
        assertThat(stockOf(remaining)).isEqualTo(4);

        Map<String, Object> soldOutRow = goodsRow(soldOut);
        assertThat(soldOutRow.get("status")).isEqualTo(GoodsStatus.OFF_SHELF.name());
        assertThat(soldOutRow.get("off_shelf_reason")).isEqualTo(OffShelfReason.SOLD_OUT.name());
        assertThat(soldOutRow.get("off_shelf_at")).isNotNull();

        Map<String, Object> remainingRow = goodsRow(remaining);
        assertThat(remainingRow.get("status")).isEqualTo(GoodsStatus.ON_SALE.name());
        assertThat(remainingRow.get("off_shelf_reason")).isNull();
    }

    @Test
    @DisplayName("任一明细缺货则整单拒绝：不扣任何一行、订单仍是已发货，补货后可重试")
    void rejectsWholeOrderWhenAnyLineIsShort() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        CategoryL2 category = createCategory();
        Goods enough = createGoods(category);
        Goods short_ = createGoods(category);
        setStock(enough, 5);
        setStock(short_, 1);

        Order order = orderOf(enough, 2, short_, 2);
        markShipped(order);
        flushAndClear();

        MvcResult rejected = complete(token, order);
        assertApiStatusAndCode(rejected, 200, ORDER_STOCK_SHORTAGE);
        assertThat(messageOf(rejected))
                .contains(short_.getId())
                .contains("needs 2, has 1")
                .doesNotContain(enough.getId());

        assertThat(stockOf(enough)).isEqualTo(5);
        assertThat(stockOf(short_)).isEqualTo(1);
        assertThat(statusOf(order)).isEqualTo("SHIPPED");

        // 补货后重试：同一张订单可以完成，扣减按明细数量一次到位。
        setStock(short_, 5);
        assertApiCode(complete(token, order), 0);
        assertThat(statusOf(order)).isEqualTo("COMPLETED");
        assertThat(stockOf(enough)).isEqualTo(3);
        assertThat(stockOf(short_)).isEqualTo(3);
    }

    @Test
    @DisplayName("只有已发货能完成；未发货时返回状态不允许且不碰库存")
    void rejectsCompletionBeforeShipping() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        BuyerAccount buyer = createBuyer();
        Goods goods = createGoods(createCategory());
        Order order = createOrder(buyer, goods, 1);

        MvcResult result = complete(token, order);

        assertApiStatusAndCode(result, 200, ORDER_STATUS_INVALID);
        assertThat(statusOf(order)).isEqualTo("PENDING_CONFIRM");
        assertThat(stockOf(goods)).isEqualTo(3);
    }

    @Test
    @DisplayName("订单不存在返回 404/60001")
    void rejectsUnknownOrder() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/seller/orders/O-NOT-EXISTS/complete")
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiStatusAndCode(result, 404, ORDER_NOT_FOUND);
    }

    private MvcResult complete(String token, Order order) throws Exception {
        return mockMvc.perform(post("/api/seller/orders/" + order.getId() + "/complete")
                        .header("Authorization", bearer(token)))
                .andReturn();
    }

    private Order orderOf(Goods first, int firstQuantity, Goods second, int secondQuantity) {
        BuyerAccount buyer = createBuyer();
        return orders.saveAndFlush(Order.place(
                orderNoGenerator.next(),
                buyer.getId(),
                List.of(
                        new OrderLine(first.getId(), first.getName(), first.getPrice(), firstQuantity),
                        new OrderLine(second.getId(), second.getName(), second.getPrice(), secondQuantity))));
    }

    /** 直接改库准备已发货夹具，改完清上下文，避免服务层读到旧的托管实体。 */
    private void markShipped(Order order) {
        jdbcTemplate.update("update simpleshop_order set status = ? where id = ?",
                OrderStatus.SHIPPED.name(), order.getId());
        flushAndClear();
    }

    private String statusOf(Order order) {
        return jdbcTemplate.queryForObject(
                "select status from simpleshop_order where id = ?", String.class, order.getId());
    }

    private int stockOf(Goods goods) {
        return stockItems.findById(goods.getId()).orElseThrow().getAvailable();
    }

    private Map<String, Object> goodsRow(Goods goods) {
        return jdbcTemplate.queryForMap(
                "select status, off_shelf_reason, off_shelf_at from simpleshop_goods where id = ?",
                goods.getId());
    }

    private String messageOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("message").asText();
    }
}
