package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.enums.OffShelfReason;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.testing.ApiTestBase;

/**
 * 买家确认收货：成交由买家推进，库存只在这一次变化。
 *
 * <p>三类库存行为各一条用例：在售扣减（扣到 0 自动下架）、已下架跳过、缺货整单拒绝。
 * 另有状态与权限边界：未发货不许确认、他人订单按不存在、重复确认不重复扣。
 */
class BuyerReceiptApiTest extends ApiTestBase {

    private static final String PASSWORD = "Buyer@1234";

    @Test
    @DisplayName("在售商品确认收货：扣减库存，扣到 0 自动下架（已售）")
    void deductsStockAndMarksSoldOut() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = shippedOrder(buyer, 1);
        Goods goods = goodsOf(order);
        setStock(goods, 1);

        assertApiCode(confirmReceipt(buyer, order.getId()), 0);

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(stockOf(goods)).isZero();
        Goods after = reloadGoods(goods);
        assertThat(after.getStatus()).isEqualTo(GoodsStatus.OFF_SHELF);
        assertThat(after.getOffShelfReason()).isEqualTo(OffShelfReason.SOLD_OUT);
    }

    @Test
    @DisplayName("已下架商品成交：不扣库存、不校验缺货，订单仍可完成")
    void offShelfGoodsCompletesWithoutDeduction() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = shippedOrder(buyer, 1);
        Goods goods = goodsOf(order);
        setStock(goods, 2);
        offShelf(goods, OffShelfReason.MANUAL);

        assertApiCode(confirmReceipt(buyer, order.getId()), 0);

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(stockOf(goods)).isEqualTo(2);
        Goods after = reloadGoods(goods);
        assertThat(after.getStatus()).isEqualTo(GoodsStatus.OFF_SHELF);
        assertThat(after.getOffShelfReason()).isEqualTo(OffShelfReason.MANUAL);
    }

    @Test
    @DisplayName("库存不足：整单拒绝，订单停在已发货、一行都不扣；补货后可重试成功")
    void rejectsShortageAndAllowsRetry() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = shippedOrder(buyer, 2);
        Goods goods = goodsOf(order);
        setStock(goods, 1);

        assertApiCode(confirmReceipt(buyer, order.getId()), 60004);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(stockOf(goods)).isEqualTo(1);

        setStock(goods, 2);
        assertApiCode(confirmReceipt(buyer, order.getId()), 0);

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(stockOf(goods)).isZero();
    }

    @Test
    @DisplayName("未发货不能确认：待支付订单直接拒绝")
    void rejectsConfirmBeforeShipping() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = placeAndPay(buyer);

        assertApiCode(confirmReceipt(buyer, order.getId()), 60002);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("不能确认别人的订单：按订单不存在处理")
    void rejectsConfirmingOthersOrder() throws Exception {
        BuyerAccount owner = createBuyer();
        Order order = shippedOrder(owner, 1);
        BuyerAccount other = createBuyer();
        String otherToken = bearer(loginBuyer(other.getUsername(), PASSWORD));

        assertApiStatusAndCode(
                mockMvc.perform(post("/api/buyer/orders/{id}/confirm-receipt", order.getId())
                                .header("Authorization", otherToken))
                        .andReturn(),
                404, 60001);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    @DisplayName("重复确认：第二次返回状态不允许，库存只扣一次")
    void confirmsOnlyOnce() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = shippedOrder(buyer, 1);
        Goods goods = goodsOf(order);
        setStock(goods, 2);

        assertApiCode(confirmReceipt(buyer, order.getId()), 0);
        assertThat(stockOf(goods)).isEqualTo(1);

        assertApiCode(confirmReceipt(buyer, order.getId()), 60002);
        assertThat(stockOf(goods)).isEqualTo(1);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    /** 下单 → 支付 → 卖家确认 → 备货完成 → 发货，返回已发货订单。 */
    private Order shippedOrder(BuyerAccount buyer, int quantity) throws Exception {
        Order order = placeOrder(buyer, quantity);
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        pay(buyerToken, order.getId(), "ALIPAY");

        String sellerToken = bearer(loginSeller("Abcd@1234"));
        advance(sellerToken, order.getId(), "confirm");
        advance(sellerToken, order.getId(), "prepare");
        mockMvc.perform(post("/api/seller/orders/{id}/ship", order.getId())
                        .header("Authorization", sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("carrier", "顺丰速运", "tracking_no", "SF123456"))))
                .andExpect(status().isOk());

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.SHIPPED);
        return order;
    }

    private Order placeAndPay(BuyerAccount buyer) throws Exception {
        Order order = placeOrder(buyer, 1);
        pay(bearer(loginBuyer(buyer.getUsername(), PASSWORD)), order.getId(), "ALIPAY");
        return order;
    }

    private Order placeOrder(BuyerAccount buyer, int quantity) throws Exception {
        var address = createAddress(buyer);
        Goods goods = createGoods(createCategory());
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        var result = mockMvc.perform(post("/api/buyer/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "address_id", address.getId(),
                                "items", List.of(Map.of("goods_id", goods.getId(),
                                        "quantity", quantity))))))
                .andExpect(status().isOk())
                .andReturn();
        return orders.findById(dataOf(result).path("order_id").asText()).orElseThrow();
    }

    private void advance(String sellerToken, String orderId, String action) throws Exception {
        mockMvc.perform(post("/api/seller/orders/{id}/{action}", orderId, action)
                        .header("Authorization", sellerToken))
                .andExpect(status().isOk());
    }

    private JsonNode pay(String token, String orderId, String channel) throws Exception {
        var result = mockMvc.perform(post("/api/buyer/orders/{id}/pay", orderId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("payment_channel", channel))))
                .andReturn();
        return dataOf(result);
    }

    private MvcResult confirmReceipt(BuyerAccount buyer, String orderId) throws Exception {
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        return mockMvc.perform(post("/api/buyer/orders/{id}/confirm-receipt", orderId)
                        .header("Authorization", token))
                .andReturn();
    }

    private Goods goodsOf(Order order) {
        String goodsId = orderItems.findByOrder_IdOrderByGoodsNameAscIdAsc(order.getId())
                .get(0)
                .getGoodsId();
        return goodsRepository.findById(goodsId).orElseThrow();
    }

    private int stockOf(Goods goods) {
        return stockItems.findById(goods.getId()).orElseThrow().getAvailable();
    }

    private Order reload(Order order) {
        flushAndClear();
        return orders.findById(order.getId()).orElseThrow();
    }

    private Goods reloadGoods(Goods goods) {
        flushAndClear();
        return goodsRepository.findById(goods.getId()).orElseThrow();
    }
}
