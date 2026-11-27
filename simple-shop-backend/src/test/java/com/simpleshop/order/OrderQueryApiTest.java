package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.address.entity.Address;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.testing.ApiTestBase;

/**
 * 订单查询：买卖两端的列表筛选、详情与明细的形状、归属隔离。
 *
 * <p>订单头与明细分两个端点：列表与详情只回订单头，明细按订单 ID 单独查，
 * 避免一层返回里出现两层分页。
 */
class OrderQueryApiTest extends ApiTestBase {

    private static final String PASSWORD = "Buyer@1234";

    @Test
    @DisplayName("买家列表只返回自己的订单，按下单时间倒序，带件数")
    void buyerListShowsOwnOrdersNewestFirst() throws Exception {
        BuyerAccount buyer = createBuyer();
        Address address = createAddress(buyer);
        Goods first = createGoods(createCategory());
        Goods second = createGoods(createCategory());
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        String olderId = place(token, address.getId(), first.getId(), 1);
        String newerId = place(token, address.getId(), second.getId(), 2);
        LocalDateTime base = LocalDateTime.now(ZoneOffset.UTC).minusDays(1);
        jdbcTemplate.update("update simpleshop_order set create_at = ?, update_at = ? where id = ?",
                base, base, olderId);
        jdbcTemplate.update("update simpleshop_order set create_at = ?, update_at = ? where id = ?",
                base.plusHours(1), base.plusHours(1), newerId);
        flushAndClear();

        BuyerAccount other = createBuyer();
        place(bearer(loginBuyer(other.getUsername(), PASSWORD)),
                createAddress(other).getId(), first.getId(), 1);

        JsonNode page = dataOf(mockMvc.perform(get("/api/buyer/orders")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(page.path("total").asLong()).isEqualTo(2);
        assertThat(page.path("items").get(0).path("order_id").asText()).isEqualTo(newerId);
        assertThat(page.path("items").get(0).path("item_count").asInt()).isEqualTo(1);
        assertThat(page.path("items").get(1).path("order_id").asText()).isEqualTo(olderId);
    }

    @Test
    @DisplayName("状态筛选只认七态；非法状态按参数错误拒绝")
    void filtersByStatusAndRejectsUnknown() throws Exception {
        BuyerAccount buyer = createBuyer();
        Address address = createAddress(buyer);
        Goods goods = createGoods(createCategory());
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        String paidId = place(token, address.getId(), goods.getId(), 1);
        mockMvc.perform(post("/api/buyer/orders/{id}/pay", paidId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("payment_channel", "ALIPAY"))))
                .andExpect(status().isOk());
        place(token, address.getId(), goods.getId(), 1);

        JsonNode paid = dataOf(mockMvc.perform(get("/api/buyer/orders")
                        .header("Authorization", token)
                        .queryParam("status", "PAID"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(paid.path("total").asLong()).isEqualTo(1);
        assertThat(paid.path("items").get(0).path("order_id").asText()).isEqualTo(paidId);

        JsonNode all = dataOf(mockMvc.perform(get("/api/buyer/orders")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(all.path("total").asLong()).isEqualTo(2);

        assertApiStatusAndCode(mockMvc.perform(get("/api/buyer/orders")
                        .header("Authorization", token)
                        .queryParam("status", "PENDING_CONFIRM"))
                .andReturn(), 400, 50002);
    }

    @Test
    @DisplayName("买家详情只回订单头，不含订单项；他人订单按不存在处理")
    void buyerDetailExcludesItems() throws Exception {
        BuyerAccount buyer = createBuyer();
        Address address = createAddress(buyer);
        Goods goods = createGoods(createCategory());
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        String orderId = place(token, address.getId(), goods.getId(), 1);

        JsonNode detail = dataOf(mockMvc.perform(get("/api/buyer/orders/{id}", orderId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(detail.has("items")).isFalse();
        assertThat(detail.path("item_count").asInt()).isEqualTo(1);
        assertThat(detail.path("buyer").path("id").asText()).isEqualTo(buyer.getId());

        BuyerAccount other = createBuyer();
        assertApiStatusAndCode(mockMvc.perform(get("/api/buyer/orders/{id}", orderId)
                        .header("Authorization", bearer(loginBuyer(
                                other.getUsername(), PASSWORD))))
                .andReturn(), 404, 60001);
    }

    @Test
    @DisplayName("订单项按下单快照返回，小计由服务端算；他人订单项按不存在处理")
    void itemsReturnSnapshotWithAmount() throws Exception {
        BuyerAccount buyer = createBuyer();
        Address address = createAddress(buyer);
        Goods goods = createGoods(createCategory());
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        String orderId = place(token, address.getId(), goods.getId(), 2);

        JsonNode items = dataOf(mockMvc.perform(get("/api/buyer/orders/{id}/items", orderId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn()).path("items");
        assertThat(items).hasSize(1);
        JsonNode item = items.get(0);
        assertThat(item.path("goods_name").asText()).isEqualTo(goods.getName());
        assertThat(item.path("quantity").asInt()).isEqualTo(2);
        assertThat(item.path("amount").decimalValue()).isEqualByComparingTo(
                item.path("unit_price").decimalValue()
                        .multiply(java.math.BigDecimal.valueOf(2)));

        BuyerAccount other = createBuyer();
        assertApiStatusAndCode(mockMvc.perform(get("/api/buyer/orders/{id}/items", orderId)
                        .header("Authorization", bearer(loginBuyer(
                                other.getUsername(), PASSWORD))))
                .andReturn(), 404, 60001);
    }

    @Test
    @DisplayName("卖家可按买家、商品与状态组合筛选")
    void sellerListFiltersByBuyerGoodsAndStatus() throws Exception {
        BuyerAccount firstBuyer = createBuyer();
        BuyerAccount secondBuyer = createBuyer();
        Goods firstGoods = createGoods(createCategory());
        Goods secondGoods = createGoods(createCategory());
        String firstToken = bearer(loginBuyer(firstBuyer.getUsername(), PASSWORD));
        String secondToken = bearer(loginBuyer(secondBuyer.getUsername(), PASSWORD));

        String firstOrder = place(firstToken, createAddress(firstBuyer).getId(),
                firstGoods.getId(), 1);
        place(secondToken, createAddress(secondBuyer).getId(), secondGoods.getId(), 1);
        mockMvc.perform(post("/api/buyer/orders/{id}/pay", firstOrder)
                        .header("Authorization", firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("payment_channel", "WECHAT"))))
                .andExpect(status().isOk());

        String seller = bearer(loginSeller("Abcd@1234"));
        assertThat(listOf(seller, Map.of("buyer_id", firstBuyer.getId()))).hasSize(1);
        assertThat(listOf(seller, Map.of("goods_id", secondGoods.getId()))).hasSize(1);
        assertThat(listOf(seller, Map.of("status", "PAID"))).hasSize(1);
        assertThat(listOf(seller, Map.of("buyer_id", firstBuyer.getId(), "status", "PAID")))
                .hasSize(1);
        assertThat(listOf(seller, Map.of("buyer_id", secondBuyer.getId(), "status", "PAID")))
                .isEmpty();
    }

    @Test
    @DisplayName("卖家可查看任意订单的详情与明细；订单不存在返回 404")
    void sellerSeesAnyOrder() throws Exception {
        BuyerAccount buyer = createBuyer();
        Address address = createAddress(buyer);
        Goods goods = createGoods(createCategory());
        String orderId = place(bearer(loginBuyer(buyer.getUsername(), PASSWORD)),
                address.getId(), goods.getId(), 1);
        String seller = bearer(loginSeller("Abcd@1234"));

        JsonNode detail = dataOf(mockMvc.perform(get("/api/seller/orders/{id}", orderId)
                        .header("Authorization", seller))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(detail.path("order_id").asText()).isEqualTo(orderId);

        JsonNode items = dataOf(mockMvc.perform(get("/api/seller/orders/{id}/items", orderId)
                        .header("Authorization", seller))
                .andExpect(status().isOk())
                .andReturn()).path("items");
        assertThat(items).hasSize(1);

        assertApiStatusAndCode(mockMvc.perform(get("/api/seller/orders/{id}", "O-NOT-EXISTS")
                        .header("Authorization", seller))
                .andReturn(), 404, 60001);
    }

    private String place(String token, String addressId, String goodsId, int quantity)
            throws Exception {
        var result = mockMvc.perform(post("/api/buyer/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "address_id", addressId,
                                "items", List.of(Map.of("goods_id", goodsId,
                                        "quantity", quantity))))))
                .andExpect(status().isOk())
                .andReturn();
        assertApiCode(result, 0);
        return dataOf(result).path("order_id").asText();
    }

    private JsonNode listOf(String seller, Map<String, String> query) throws Exception {
        var request = get("/api/seller/orders").header("Authorization", seller);
        for (Map.Entry<String, String> entry : query.entrySet()) {
            request = request.queryParam(entry.getKey(), entry.getValue());
        }
        return dataOf(mockMvc.perform(request).andExpect(status().isOk()).andReturn())
                .path("items");
    }
}
