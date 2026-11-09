package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.OffShelfReason;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.entity.OrderItem;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.testing.ApiTestBase;

class BuyerOrderApiTest extends ApiTestBase {

    private static final String SELLER_PASSWORD = "Abcd@1234";
    private static final int GOODS_NOT_FOUND = 20011;
    private static final int STOCK_INVALID = 20015;
    private static final int GOODS_NOT_ON_SALE = 20019;
    private static final int PARAM_INVALID = 50002;
    private static final int ORDER_NOT_FOUND = 60001;
    private static final int ORDER_NOT_CANCELABLE = 60003;

    @Test
    @DisplayName("下单成功：按下单快照写明细，总额由服务端计算，不扣库存")
    void placesOrderWithSnapshotsAndTotal() throws Exception {
        BuyerAccount buyer = createBuyer();
        CategoryL2 category = createCategory();
        Goods first = createGoods(category);
        Goods second = createGoods(category);
        String token = loginBuyer(buyer.getUsername(), "Buyer@1234");

        MvcResult result = place(token, List.of(item(first, 2), item(second, 1)));

        assertApiCode(result, 0);
        JsonNode data = dataOf(result);
        assertThat(data.path("order_no").asText()).matches("SO\\d{14}");
        assertThat(data.path("total_amount").decimalValue())
                .isEqualByComparingTo(new BigDecimal("299.97"));
        assertThat(data.path("status").asText()).isEqualTo("PENDING_CONFIRM");
        assertThat(data.path("create_at").asText()).endsWith("Z");

        Order order = orders.findById(data.path("order_id").asText()).orElseThrow();
        List<OrderItem> orderItemsOfOrder = orderItems.findByOrder_IdOrderByGoodsNameAscIdAsc(
                order.getId());
        assertThat(order.getBuyerId()).isEqualTo(buyer.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRM);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("299.97"));
        assertThat(orderItemsOfOrder).hasSize(2);
        assertThat(orderItemsOfOrder).extracting(OrderItem::getGoodsId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(orderItemsOfOrder.stream()
                .filter(item -> item.getGoodsId().equals(first.getId()))
                .findFirst()
                .orElseThrow()
                .getQuantity()).isEqualTo(2);

        // 下单只校验、不预占：库存仍是原值。
        assertThat(stockItems.findById(first.getId()).orElseThrow().getAvailable()).isEqualTo(3);
        assertThat(stockItems.findById(second.getId()).orElseThrow().getAvailable()).isEqualTo(3);
    }

    @Test
    @DisplayName("同一订单内重复商品按参数错误拒绝，不做数量合并、不落单")
    void rejectsDuplicateGoodsAsInvalidRequest() throws Exception {
        BuyerAccount buyer = createBuyer();
        Goods goods = createGoods(createCategory());
        String token = loginBuyer(buyer.getUsername(), "Buyer@1234");

        MvcResult result = place(token, List.of(item(goods, 1), item(goods, 2)));

        assertApiStatusAndCode(result, 400, PARAM_INVALID);
        assertThat(messageOf(result)).contains(goods.getId());
        assertThat(orderCountOf(buyer)).isZero();
    }

    @Test
    @DisplayName("商品不存在、已下架、库存不足各自返回对应业务码，整单不落库")
    void rejectsUnavailableItems() throws Exception {
        BuyerAccount buyer = createBuyer();
        CategoryL2 category = createCategory();
        Goods offShelfGoods = offShelf(createGoods(category), OffShelfReason.MANUAL);
        Goods shortOfStock = createGoods(category);
        setStock(shortOfStock, 1);
        String token = loginBuyer(buyer.getUsername(), "Buyer@1234");

        MvcResult missing = place(token, List.of(item("G-NOT-EXISTS", 1)));
        assertApiStatusAndCode(missing, 404, GOODS_NOT_FOUND);
        assertThat(messageOf(missing)).contains("G-NOT-EXISTS");

        MvcResult offShelfResult = place(token, List.of(item(offShelfGoods, 1)));
        assertApiStatusAndCode(offShelfResult, 200, GOODS_NOT_ON_SALE);
        assertThat(messageOf(offShelfResult)).contains(offShelfGoods.getId());

        MvcResult insufficient = place(token, List.of(item(shortOfStock, 2)));
        assertApiStatusAndCode(insufficient, 200, STOCK_INVALID);
        assertThat(messageOf(insufficient)).contains(shortOfStock.getId());

        assertThat(orderCountOf(buyer)).isZero();
        assertThat(stockItems.findById(shortOfStock.getId()).orElseThrow().getAvailable())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("有问题的明细让整单失败：合法明细也不会被受理")
    void rejectsTheWholeOrderWhenAnyLineFails() throws Exception {
        BuyerAccount buyer = createBuyer();
        CategoryL2 category = createCategory();
        Goods available = createGoods(category);
        Goods offShelfGoods = offShelf(createGoods(category), OffShelfReason.STOCK_ZERO);
        String token = loginBuyer(buyer.getUsername(), "Buyer@1234");

        MvcResult result = place(token, List.of(
                item(available, 1),
                item(offShelfGoods, 1),
                item("G-NOT-EXISTS", 1)));

        assertApiStatusAndCode(result, 200, GOODS_NOT_ON_SALE);
        assertThat(messageOf(result)).contains(offShelfGoods.getId());
        assertThat(orderCountOf(buyer)).isZero();
    }

    @Test
    @DisplayName("请求体形状错误走参数校验：空明细、数量为 0 返回 400")
    void validatesRequestShape() throws Exception {
        BuyerAccount buyer = createBuyer();
        Goods goods = createGoods(createCategory());
        String token = loginBuyer(buyer.getUsername(), "Buyer@1234");

        MvcResult empty = place(token, List.of());
        assertApiFieldError(empty, "items");

        MvcResult zeroQuantity = place(token, List.of(Map.of(
                "goods_id", goods.getId(), "quantity", 0)));
        assertApiFieldError(zeroQuantity, "items[0].quantity");
    }

    @Test
    @DisplayName("下单必须买家令牌：匿名与卖家令牌都不放行")
    void requiresBuyerSession() throws Exception {
        Goods goods = createGoods(createCategory());
        String body = json(Map.of("items", List.of(item(goods, 1))));

        MvcResult anonymous = mockMvc.perform(post("/api/buyer/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        assertApiStatusAndCode(anonymous, 401, 10002);

        MvcResult seller = mockMvc.perform(post("/api/buyer/orders")
                        .header("Authorization", bearer(loginSeller(SELLER_PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        assertApiStatusAndCode(seller, 401, 10002);
    }

    @Test
    @DisplayName("买家在发货前可取消：整单取消、库存不变")
    void buyerCancelsBeforeShipping() throws Exception {
        BuyerAccount buyer = createBuyer();
        Goods goods = createGoods(createCategory());
        Order order = createOrder(buyer, goods, 2);
        String token = loginBuyer(buyer.getUsername(), "Buyer@1234");

        MvcResult result = mockMvc.perform(post(
                        "/api/buyer/orders/" + order.getId() + "/cancel")
                        .header("Authorization", bearer(token)))
                .andReturn();

        assertApiCode(result, 0);
        assertThat(dataOf(result).path("status").asText()).isEqualTo("CANCELED");
        assertThat(stockItems.findById(goods.getId()).orElseThrow().getAvailable()).isEqualTo(3);
    }

    @Test
    @DisplayName("已发货后买家不可取消，订单停在已发货")
    void buyerCannotCancelAfterShipping() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = createOrder(buyer, createGoods(createCategory()), 1);
        jdbcTemplate.update("update simpleshop_order set status = ? where id = ?",
                OrderStatus.SHIPPED.name(), order.getId());
        flushAndClear();
        String token = loginBuyer(buyer.getUsername(), "Buyer@1234");

        MvcResult result = mockMvc.perform(post(
                        "/api/buyer/orders/" + order.getId() + "/cancel")
                        .header("Authorization", bearer(token)))
                .andReturn();

        assertApiStatusAndCode(result, 200, ORDER_NOT_CANCELABLE);
    }

    @Test
    @DisplayName("买家取消他人订单时按订单不存在处理")
    void buyerCannotCancelOthersOrder() throws Exception {
        BuyerAccount owner = createBuyer();
        BuyerAccount other = createBuyer();
        Order order = createOrder(owner, createGoods(createCategory()), 1);
        String token = loginBuyer(other.getUsername(), "Buyer@1234");

        MvcResult result = mockMvc.perform(post(
                        "/api/buyer/orders/" + order.getId() + "/cancel")
                        .header("Authorization", bearer(token)))
                .andReturn();

        assertApiStatusAndCode(result, 404, ORDER_NOT_FOUND);
    }

    private MvcResult place(String token, List<Map<String, Object>> items) throws Exception {
        return mockMvc.perform(post("/api/buyer/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("items", items))))
                .andReturn();
    }

    private Map<String, Object> item(Goods goods, int quantity) {
        return item(goods.getId(), quantity);
    }

    private Map<String, Object> item(String goodsId, int quantity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("goods_id", goodsId);
        item.put("quantity", quantity);
        return item;
    }

    private String messageOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("message").asText();
    }

    private long orderCountOf(BuyerAccount buyer) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from simpleshop_order where fk_buyer_id = ?", Long.class,
                buyer.getId());
        return count == null ? 0 : count;
    }
}
