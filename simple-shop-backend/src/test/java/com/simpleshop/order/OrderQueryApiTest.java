package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.OffShelfReason;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.entity.OrderLine;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.testing.ApiTestBase;

class OrderQueryApiTest extends ApiTestBase {

    private static final String SELLER_PASSWORD = "Abcd@1234";
    private static final int ORDER_NOT_FOUND = 60001;
    private static final int PARAM_INVALID = 50002;

    @Test
    @DisplayName("买家订单列表只返回自己的订单，按下单时间倒序，带买家名与件数")
    void buyerListShowsOwnOrdersNewestFirst() throws Exception {
        BuyerAccount buyer = createBuyer();
        BuyerAccount other = createBuyer();
        Goods goods = createGoods(createCategory());

        Order older = createOrder(buyer, goods, 1);
        Order newer = createOrder(buyer, goods, 2);
        createOrder(other, goods, 1);
        LocalDateTime base = LocalDateTime.now(ZoneOffset.UTC).minusDays(1);
        backdate(older, base);
        backdate(newer, base.plusHours(1));
        flushAndClear();

        String token = loginBuyer(buyer.getUsername(), "Buyer@1234");
        MvcResult result = mockMvc.perform(get("/api/buyer/orders")
                        .header("Authorization", bearer(token))
                        .param("page", "1").param("page_size", "10"))
                .andReturn();

        assertApiCode(result, 0);
        JsonNode data = dataOf(result);
        assertThat(data.path("total").asLong()).isEqualTo(2);
        JsonNode items = data.path("items");
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(0).path("order_id").asText()).isEqualTo(newer.getId());
        assertThat(items.get(1).path("order_id").asText()).isEqualTo(older.getId());
        assertThat(items.get(0).path("item_count").asInt()).isEqualTo(1);
        assertThat(items.get(0).path("buyer").path("username").asText())
                .isEqualTo(buyer.getUsername());
        assertThat(items.get(0).path("buyer").path("id").asText()).isEqualTo(buyer.getId());
    }

    @Test
    @DisplayName("买家订单列表可按状态筛选，空状态返回全部")
    void buyerListFiltersByStatus() throws Exception {
        BuyerAccount buyer = createBuyer();
        Goods goods = createGoods(createCategory());
        Order pending = createOrder(buyer, goods, 1);
        Order canceled = createOrder(buyer, goods, 1);
        markStatus(canceled, OrderStatus.CANCELED);
        String token = loginBuyer(buyer.getUsername(), "Buyer@1234");

        JsonNode canceledOnly = dataOf(mockMvc.perform(get("/api/buyer/orders")
                        .header("Authorization", bearer(token))
                        .param("status", "CANCELED"))
                .andReturn());
        assertThat(canceledOnly.path("items").size()).isEqualTo(1);
        assertThat(canceledOnly.path("items").get(0).path("order_id").asText())
                .isEqualTo(canceled.getId());

        JsonNode all = dataOf(mockMvc.perform(get("/api/buyer/orders")
                        .header("Authorization", bearer(token)))
                .andReturn());
        assertThat(all.path("total").asLong()).isEqualTo(2);
        assertThat(all.path("items").findValuesAsText("order_id"))
                .containsExactlyInAnyOrder(pending.getId(), canceled.getId());
    }

    @Test
    @DisplayName("买家订单详情只回订单头，不含订单项；他人订单按不存在处理")
    void buyerDetailExcludesItems() throws Exception {
        BuyerAccount buyer = createBuyer();
        BuyerAccount other = createBuyer();
        Goods first = createGoods(createCategory());
        Goods second = createGoods(createCategory());
        Order order = orderOf(buyer, first, 2, second, 1);
        String token = loginBuyer(buyer.getUsername(), "Buyer@1234");

        MvcResult result = mockMvc.perform(get("/api/buyer/orders/" + order.getId())
                        .header("Authorization", bearer(token)))
                .andReturn();

        assertApiCode(result, 0);
        JsonNode data = dataOf(result);
        assertThat(data.path("order_no").asText()).isEqualTo(order.getOrderNo());
        assertThat(data.path("status").asText()).isEqualTo("PENDING_CONFIRM");
        assertThat(data.path("total_amount").decimalValue())
                .isEqualByComparingTo(new BigDecimal("299.97"));
        assertThat(data.path("item_count").asInt()).isEqualTo(2);
        assertThat(data.path("create_at").asText()).endsWith("Z");
        assertThat(data.path("buyer").path("username").asText()).isEqualTo(buyer.getUsername());
        assertThat(data.has("items")).isFalse();

        String otherToken = loginBuyer(other.getUsername(), "Buyer@1234");
        MvcResult foreign = mockMvc.perform(get("/api/buyer/orders/" + order.getId())
                        .header("Authorization", bearer(otherToken)))
                .andReturn();
        assertApiStatusAndCode(foreign, 404, ORDER_NOT_FOUND);

        MvcResult unknown = mockMvc.perform(get("/api/buyer/orders/O-NOT-EXISTS")
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiStatusAndCode(unknown, 404, ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("买家订单项按下单快照返回，含服务端算好的小计；他人订单项按不存在处理")
    void buyerItemsReturnSnapshot() throws Exception {
        BuyerAccount buyer = createBuyer();
        BuyerAccount other = createBuyer();
        CategoryL2 category = createCategory();
        Goods first = createGoods(category);
        Goods second = createGoods(category);
        Order order = orderOf(buyer, first, 2, second, 1);
        String token = loginBuyer(buyer.getUsername(), "Buyer@1234");

        MvcResult result = mockMvc.perform(get("/api/buyer/orders/" + order.getId() + "/items")
                        .header("Authorization", bearer(token)))
                .andReturn();

        assertApiCode(result, 0);
        JsonNode items = dataOf(result).path("items");
        assertThat(items.size()).isEqualTo(2);
        JsonNode firstItem = items.get(0);
        assertThat(firstItem.path("goods_id").asText()).isIn(first.getId(), second.getId());
        assertThat(firstItem.path("goods_name").asText()).isNotBlank();
        assertThat(firstItem.path("unit_price").decimalValue())
                .isEqualByComparingTo(new BigDecimal("99.99"));

        JsonNode twoOfFirst = null;
        for (JsonNode item : items) {
            if (item.path("goods_id").asText().equals(first.getId())) {
                twoOfFirst = item;
            }
        }
        assertThat(twoOfFirst).isNotNull();
        assertThat(twoOfFirst.path("quantity").asInt()).isEqualTo(2);
        assertThat(twoOfFirst.path("amount").decimalValue())
                .isEqualByComparingTo(new BigDecimal("199.98"));

        String otherToken = loginBuyer(other.getUsername(), "Buyer@1234");
        MvcResult foreign = mockMvc.perform(get("/api/buyer/orders/" + order.getId() + "/items")
                        .header("Authorization", bearer(otherToken)))
                .andReturn();
        assertApiStatusAndCode(foreign, 404, ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("卖家订单列表可按客户、商品与状态组合筛选")
    void sellerListFiltersByBuyerGoodsAndStatus() throws Exception {
        BuyerAccount buyerA = createBuyer();
        BuyerAccount buyerB = createBuyer();
        CategoryL2 category = createCategory();
        Goods goodsX = createGoods(category);
        Goods goodsY = createGoods(category);

        Order orderAX = orderOf(buyerA, goodsX, 1, goodsY, 1);
        Order orderBY = createOrder(buyerB, goodsY, 1);
        markStatus(orderBY, OrderStatus.CONFIRMED);
        String token = loginSeller(SELLER_PASSWORD);

        MvcResult all = listAsSeller(token, Map.of());
        assertThat(dataOf(all).path("total").asLong()).isEqualTo(2);

        MvcResult byBuyer = listAsSeller(token, Map.of("buyer_id", buyerB.getId()));
        assertThat(orderIdsOf(byBuyer)).containsExactly(orderBY.getId());

        MvcResult byGoods = listAsSeller(token, Map.of("goods_id", goodsX.getId()));
        assertThat(orderIdsOf(byGoods)).containsExactly(orderAX.getId());

        MvcResult byStatus = listAsSeller(token, Map.of("status", "CONFIRMED"));
        assertThat(orderIdsOf(byStatus)).containsExactly(orderBY.getId());

        MvcResult combined = listAsSeller(token, Map.of(
                "buyer_id", buyerA.getId(), "goods_id", goodsY.getId(), "status", "PENDING_CONFIRM"));
        assertThat(orderIdsOf(combined)).containsExactly(orderAX.getId());

        MvcResult noMatch = listAsSeller(token, Map.of(
                "buyer_id", buyerB.getId(), "goods_id", goodsX.getId()));
        assertThat(dataOf(noMatch).path("total").asLong()).isZero();
    }

    @Test
    @DisplayName("卖家可看任意订单的详情与明细；订单不存在返回 404")
    void sellerReadsAnyOrder() throws Exception {
        BuyerAccount buyer = createBuyer();
        Goods goods = createGoods(createCategory());
        Order order = createOrder(buyer, goods, 2);
        String token = loginSeller(SELLER_PASSWORD);

        MvcResult detail = mockMvc.perform(get("/api/seller/orders/" + order.getId())
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(detail, 0);
        assertThat(dataOf(detail).path("buyer").path("username").asText())
                .isEqualTo(buyer.getUsername());
        assertThat(dataOf(detail).path("item_count").asInt()).isEqualTo(1);

        MvcResult items = mockMvc.perform(get("/api/seller/orders/" + order.getId() + "/items")
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(items, 0);
        assertThat(dataOf(items).path("items").get(0).path("quantity").asInt()).isEqualTo(2);

        MvcResult unknown = mockMvc.perform(get("/api/seller/orders/O-NOT-EXISTS")
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiStatusAndCode(unknown, 404, ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("非法状态参数按参数错误拒绝")
    void rejectsInvalidStatus() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);

        MvcResult result = mockMvc.perform(get("/api/seller/orders")
                        .header("Authorization", bearer(token))
                        .param("status", "NOT_A_STATUS"))
                .andReturn();
        assertApiStatusAndCode(result, 400, PARAM_INVALID);

        BuyerAccount buyer = createBuyer();
        String buyerToken = loginBuyer(buyer.getUsername(), "Buyer@1234");
        MvcResult buyerResult = mockMvc.perform(get("/api/buyer/orders")
                        .header("Authorization", bearer(buyerToken))
                        .param("status", "NOT_A_STATUS"))
                .andReturn();
        assertApiStatusAndCode(buyerResult, 400, PARAM_INVALID);
    }

    @Test
    @DisplayName("商品下架与改挂类别后，订单详情与明细仍按下单快照展示")
    void snapshotSurvivesGoodsChanges() throws Exception {
        BuyerAccount buyer = createBuyer();
        CategoryL2 category = createCategory();
        CategoryL2 otherCategory = createCategory();
        Goods goods = createGoods(category);
        Order order = createOrder(buyer, goods, 2);

        Goods managed = goodsRepository.findById(goods.getId()).orElseThrow();
        managed.offShelf(OffShelfReason.MANUAL);
        managed.changeCategory(otherCategory);
        goodsRepository.saveAndFlush(managed);
        flushAndClear();

        String token = loginBuyer(buyer.getUsername(), "Buyer@1234");
        MvcResult detail = mockMvc.perform(get("/api/buyer/orders/" + order.getId())
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(detail, 0);
        assertThat(dataOf(detail).path("total_amount").decimalValue())
                .isEqualByComparingTo(new BigDecimal("199.98"));
        assertThat(dataOf(detail).path("status").asText()).isEqualTo("PENDING_CONFIRM");

        MvcResult items = mockMvc.perform(get("/api/buyer/orders/" + order.getId() + "/items")
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(items, 0);
        JsonNode item = dataOf(items).path("items").get(0);
        assertThat(item.path("goods_id").asText()).isEqualTo(goods.getId());
        assertThat(item.path("goods_name").asText()).isEqualTo(goods.getName());
        assertThat(item.path("unit_price").decimalValue())
                .isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(item.path("quantity").asInt()).isEqualTo(2);
        assertThat(item.path("amount").decimalValue())
                .isEqualByComparingTo(new BigDecimal("199.98"));
    }

    private MvcResult listAsSeller(String token, Map<String, String> params) throws Exception {
        var request = get("/api/seller/orders").header("Authorization", bearer(token));
        for (Map.Entry<String, String> entry : params.entrySet()) {
            request = request.param(entry.getKey(), entry.getValue());
        }
        return mockMvc.perform(request).andReturn();
    }

    private List<String> orderIdsOf(MvcResult result) throws Exception {
        return dataOf(result).path("items").findValuesAsText("order_id");
    }

    private Order orderOf(BuyerAccount buyer, Goods first, int firstQuantity, Goods second, int secondQuantity) {
        return orders.saveAndFlush(Order.place(
                orderNoGenerator.next(),
                buyer.getId(),
                List.of(
                        new OrderLine(first.getId(), first.getName(), first.getPrice(), firstQuantity),
                        new OrderLine(second.getId(), second.getName(), second.getPrice(), secondQuantity))));
    }

    private void backdate(Order order, LocalDateTime time) {
        jdbcTemplate.update("update simpleshop_order set create_at = ?, update_at = ? where id = ?",
                time, time, order.getId());
    }

    private void markStatus(Order order, OrderStatus status) {
        jdbcTemplate.update("update simpleshop_order set status = ? where id = ?",
                status.name(), order.getId());
        flushAndClear();
    }
}
