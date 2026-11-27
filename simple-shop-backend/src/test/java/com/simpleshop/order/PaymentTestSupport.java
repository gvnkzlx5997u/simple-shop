package com.simpleshop.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.address.entity.Address;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.order.entity.Order;
import com.simpleshop.testing.ApiTestBase;

/**
 * 支付相关接口测试的公共夹具：建买家、建地址、下单、发起支付。
 *
 * <p>下单必须选地址，而地址接口要到 T3 才有，所以这里直接落一条地址行——
 * 测的是支付，不是地址簿。
 */
abstract class PaymentTestSupport extends ApiTestBase {

    protected static final String PASSWORD = "Buyer@1234";

    /** 下单并返回响应体：需要看 pay_deadline 这类只在下单响应里出现的字段时用它。 */
    protected JsonNode placeOrderData(BuyerAccount buyer) throws Exception {
        Address address = createAddress(buyer);
        Goods goods = createGoods(createCategory());
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        MvcResult placed = mockMvc.perform(post("/api/buyer/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "address_id", address.getId(),
                                "items", List.of(Map.of("goods_id", goods.getId(), "quantity", 1))))))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(placed);
    }

    protected Order placeOrder(BuyerAccount buyer) throws Exception {
        return orders.findById(placeOrderData(buyer).path("order_id").asText()).orElseThrow();
    }

    protected MvcResult pay(String token, String orderId, String channel) throws Exception {
        return mockMvc.perform(post("/api/buyer/orders/{id}/pay", orderId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("payment_channel", channel))))
                .andReturn();
    }

    /** 清掉持久化上下文再读，避免拿到同一个实例而看不到数据库里的真实状态。 */
    protected Order reload(Order order) {
        flushAndClear();
        return orders.findById(order.getId()).orElseThrow();
    }
}
