package com.simpleshop.aftersale;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.address.entity.Address;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.order.entity.Order;
import com.simpleshop.testing.ApiTestBase;

/**
 * 售后相关测试的公共夹具：造一笔交易完成的订单、上传凭证图、提交申请。
 *
 * <p>申请售后必须先有「交易完成」的订单，这条链路要跨支付、发货与确认收货；
 * 放在这里，买卖两侧的用例都复用同一套造数。
 */
abstract class AftersaleTestSupport extends ApiTestBase {

    protected static final String PASSWORD = "Buyer@1234";

/** 下单 → 支付 → 卖家三步 → 买家确认收货，返回交易完成的订单。 */
    protected Order completedOrder(BuyerAccount buyer) throws Exception {
        Order order = pendingOrder(buyer);
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        pay(token, order.getId(), "ALIPAY");

        String sellerToken = bearer(loginSeller("Abcd@1234"));
        mockMvc.perform(post("/api/seller/orders/{id}/confirm", order.getId())
                .header("Authorization", sellerToken)).andExpect(status().isOk());
        mockMvc.perform(post("/api/seller/orders/{id}/prepare", order.getId())
                .header("Authorization", sellerToken)).andExpect(status().isOk());
        mockMvc.perform(post("/api/seller/orders/{id}/ship", order.getId())
                        .header("Authorization", sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("carrier", "顺丰速运", "tracking_no", "SF1"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/buyer/orders/{id}/confirm-receipt", order.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk());

        flushAndClear();
        return orders.findById(order.getId()).orElseThrow();
    }

    protected Order pendingOrder(BuyerAccount buyer) throws Exception {
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
        return orders.findById(dataOf(placed).path("order_id").asText()).orElseThrow();
    }

    protected String firstItemId(Order order) {
        return orderItems.findByOrder_IdOrderByGoodsNameAscIdAsc(order.getId())
                .get(0)
                .getId();
    }

    protected void pay(String token, String orderId, String channel) throws Exception {
        mockMvc.perform(post("/api/buyer/orders/{id}/pay", orderId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("payment_channel", channel))))
                .andExpect(status().isOk());
    }

    protected MvcResult apply(
            String token, String itemId, String title, String description, List<String> urls)
            throws Exception {
        return mockMvc.perform(post("/api/buyer/aftersales")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "order_item_id", itemId,
                                "title", title,
                                "description", description,
                                "image_urls", urls))))
                .andReturn();
    }

    protected String uploadImage(String token) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/buyer/media")
                        .file(jpeg())
                        .param("purpose", "AFTERSALE_IMAGE")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn();
        assertApiCode(result, 0);
        return dataOf(result).path("url").asText();
    }

    protected String uploadGoodsImage() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/seller/media")
                        .file(jpeg())
                        .param("purpose", "GOODS_IMAGE")
                        .header("Authorization", bearer(loginSeller("Abcd@1234"))))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(result).path("url").asText();
    }

    protected static MockMultipartFile jpeg() throws Exception {
        byte[] bytes = new ClassPathResource("images/test.jpg")
                .getInputStream()
                .readAllBytes();
        return new MockMultipartFile("file", "test.jpg", "image/jpeg", bytes);
    }
}
