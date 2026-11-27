package com.simpleshop.order;

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
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.address.entity.Address;
import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.OffShelfReason;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.entity.OrderItem;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.testing.ApiTestBase;

/**
 * 买家下单：快照、总额、校验与隔离。
 *
 * <p>与阶段三的差别：下单直接进入待支付，必须带存在且属于自己的收货地址；
 * 状态从六态改成七态后，「下单后是不是待确认」这类断言全部改写。
 */
class BuyerOrderApiTest extends ApiTestBase {

    private static final String PASSWORD = "Buyer@1234";

    @Test
    @DisplayName("下单成功：明细按下单快照落库，总额由服务端算，库存不预占")
    void placesOrderWithSnapshotsAndTotal() throws Exception {
        BuyerAccount buyer = createBuyer();
        Address address = createAddress(buyer);
        CategoryL2 category = createCategory();
        Goods first = createGoods(category);
        Goods second = createGoods(category);
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        MvcResult result = place(token, address.getId(),
                List.of(item(first, 2), item(second, 1)));

        assertApiCode(result, 0);
        JsonNode data = dataOf(result);
        assertThat(data.path("order_no").asText()).matches("SO\\d{14}");
        assertThat(data.path("total_amount").decimalValue())
                .isEqualByComparingTo(new BigDecimal("299.97"));
        assertThat(data.path("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(data.path("pay_deadline").asText()).endsWith("Z");

        Order order = orders.findById(data.path("order_id").asText()).orElseThrow();
        assertThat(order.getReceiverName()).isEqualTo("张三");
        assertThat(order.getReceiverAddress()).isEqualTo("北京市朝阳区某路 1 号");
        assertThat(stockItems.findById(first.getId()).orElseThrow().getAvailable()).isEqualTo(3);

        List<OrderItem> items = orderItems.findByOrder_IdOrderByGoodsNameAscIdAsc(order.getId());
        assertThat(items).hasSize(2);
        assertThat(items).extracting(OrderItem::getGoodsId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    @DisplayName("同一订单内重复商品按参数错误拒绝，不落单")
    void rejectsDuplicateGoods() throws Exception {
        BuyerAccount buyer = createBuyer();
        Address address = createAddress(buyer);
        Goods goods = createGoods(createCategory());
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        // 重复商品属于「调用方把明细拼错了」，按参数错误返回 400 + 50002
        assertApiStatusAndCode(place(token, address.getId(),
                List.of(item(goods, 1), item(goods, 2))), 400, 50002);
        assertThat(orders.count()).isZero();
    }

    @Test
    @DisplayName("商品不存在、已下架、库存不足各自返回业务码，整单不落库")
    void rejectsUnavailableItems() throws Exception {
        BuyerAccount buyer = createBuyer();
        Address address = createAddress(buyer);
        CategoryL2 category = createCategory();
        Goods offShelfGoods = createGoods(category);
        offShelf(offShelfGoods, OffShelfReason.MANUAL);
        Goods shortStock = createGoods(category);
        setStock(shortStock, 1);
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        // 商品不存在是 404 + 20011，不是 200
        assertApiStatusAndCode(
                place(token, address.getId(), List.of(item("G-NOT-EXISTS", 1))), 404, 20011);
        assertApiCode(place(token, address.getId(), List.of(item(offShelfGoods, 1))), 20019);
        assertApiCode(place(token, address.getId(), List.of(item(shortStock, 2))), 20015);
        assertThat(orders.count()).isZero();
    }

    @Test
    @DisplayName("有问题的明细让整单失败：合法明细也不会被受理")
    void rejectsWholeOrderWhenAnyLineFails() throws Exception {
        BuyerAccount buyer = createBuyer();
        Address address = createAddress(buyer);
        CategoryL2 category = createCategory();
        Goods available = createGoods(category);
        Goods offShelfGoods = createGoods(category);
        offShelf(offShelfGoods, OffShelfReason.MANUAL);
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        assertApiCode(place(token, address.getId(),
                List.of(item(available, 1), item(offShelfGoods, 1))), 20019);
        assertThat(orders.count()).isZero();
    }

    @Test
    @DisplayName("请求体形状错误走参数校验：空明细、数量 0、缺地址都是 400")
    void validatesRequestShape() throws Exception {
        BuyerAccount buyer = createBuyer();
        Address address = createAddress(buyer);
        Goods goods = createGoods(createCategory());
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        MvcResult emptyItems = mockMvc.perform(post("/api/buyer/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("address_id", address.getId(), "items", List.of()))))
                .andReturn();
        assertApiStatusAndCode(emptyItems, 400, 50002);

        MvcResult zeroQuantity = mockMvc.perform(post("/api/buyer/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("address_id", address.getId(),
                                "items", List.of(Map.of("goods_id", goods.getId(),
                                        "quantity", 0))))))
                .andReturn();
        assertApiStatusAndCode(zeroQuantity, 400, 50002);

        MvcResult noAddress = mockMvc.perform(post("/api/buyer/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("items", List.of(item(goods, 1))))))
                .andReturn();
        assertApiFieldError(noAddress, "address_id");
    }

    @Test
    @DisplayName("下单必须买家令牌：匿名与卖家令牌都不放行")
    void requiresBuyerSession() throws Exception {
        BuyerAccount buyer = createBuyer();
        Address address = createAddress(buyer);
        Goods goods = createGoods(createCategory());
        String body = json(Map.of("address_id", address.getId(),
                "items", List.of(item(goods, 1))));

        mockMvc.perform(post("/api/buyer/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/buyer/orders")
                        .header("Authorization", bearer(loginSeller("Abcd@1234")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("买家订单列表只返回自己的订单")
    void buyerSeesOnlyOwnOrders() throws Exception {
        BuyerAccount buyer = createBuyer();
        Address address = createAddress(buyer);
        Goods goods = createGoods(createCategory());
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        String orderId = dataOf(place(token, address.getId(), List.of(item(goods, 1))))
                .path("order_id").asText();

        BuyerAccount other = createBuyer();
        String otherToken = bearer(loginBuyer(other.getUsername(), PASSWORD));
        Address otherAddress = createAddress(other);
        place(otherToken, otherAddress.getId(), List.of(item(goods, 1)));

        JsonNode mine = dataOf(mockMvc.perform(get("/api/buyer/orders")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(mine.path("total").asLong()).isEqualTo(1);
        assertThat(mine.path("items").get(0).path("order_id").asText()).isEqualTo(orderId);
        assertThat(mine.path("items").get(0).path("status").asText())
                .isEqualTo(OrderStatus.PENDING_PAYMENT.name());
    }

    private MvcResult place(String token, String addressId, List<Map<String, Object>> items)
            throws Exception {
        return mockMvc.perform(post("/api/buyer/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("address_id", addressId, "items", items))))
                .andReturn();
    }

    private static Map<String, Object> item(Goods goods, int quantity) {
        return Map.of("goods_id", goods.getId(), "quantity", quantity);
    }

    private static Map<String, Object> item(String goodsId, int quantity) {
        return Map.of("goods_id", goodsId, "quantity", quantity);
    }
}
