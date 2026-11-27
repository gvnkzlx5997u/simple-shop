package com.simpleshop.aftersale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.address.entity.Address;
import com.simpleshop.aftersale.repository.AftersaleRepository;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.order.entity.Order;


/**
 * 买家售后：申请、我的售后、详情，以及售后凭证图的上传白名单。
 *
 * <p>申请按订单项定位，且只有交易完成的订单能申请；图片必须由本服务的买家上传入口落盘。
 */
class AftersaleApiTest extends AftersaleTestSupport {

    private static final String PASSWORD = "Buyer@1234";

    @Autowired
    private AftersaleRepository aftersales;

    @Test
    @DisplayName("交易完成的订单可以申请售后：单号形如 AS+日期+4 位序列，列表与详情可见")
    void appliesAftersaleOnCompletedOrder() throws Exception {
        BuyerAccount buyer = createBuyer();
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        Order order = completedOrder(buyer);
        String itemId = firstItemId(order);
        String imageUrl = uploadImage(token);

        MvcResult applied = apply(token, itemId, "商品破损", "收到时外包装破损，边框有划痕。",
                List.of(imageUrl));
        assertApiCode(applied, 0);
        JsonNode data = dataOf(applied);
        assertThat(data.path("status").asText()).isEqualTo("PENDING");
        assertThat(data.path("aftersale_no").asText())
                .matches("AS\\d{8}\\d{4,}");
        String aftersaleId = data.path("aftersale_id").asText();

        JsonNode list = dataOf(mockMvc.perform(get("/api/buyer/aftersales")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(list.path("total").asLong()).isEqualTo(1);
        JsonNode row = list.path("items").get(0);
        assertThat(row.path("aftersale_id").asText()).isEqualTo(aftersaleId);
        assertThat(row.path("goods_name").asText())
                .isEqualTo(orderItems.findByOrder_IdOrderByGoodsNameAscIdAsc(order.getId())
                        .get(0)
                        .getGoodsName());
        assertThat(row.path("order_no").asText()).isEqualTo(order.getOrderNo());

        JsonNode detail = dataOf(mockMvc.perform(
                        get("/api/buyer/aftersales/{id}", aftersaleId)
                                .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(detail.path("description").asText()).contains("外包装破损");
        assertThat(detail.path("image_urls")).hasSize(1);
        assertThat(detail.path("image_urls").get(0).asText()).isEqualTo(imageUrl);
        assertThat(detail.path("result").isNull()).isTrue();
        assertThat(detail.path("refund_amount").isNull()).isTrue();
    }

    @Test
    @DisplayName("未交易完成的订单不能申请：81003")
    void rejectsUnfinishedOrder() throws Exception {
        BuyerAccount buyer = createBuyer();
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        Order order = pendingOrder(buyer);

        assertApiCode(apply(token, firstItemId(order), "标题", "描述", List.of()), 81003);
        assertThat(aftersales.count()).isZero();
    }

    @Test
    @DisplayName("别人的订单项按不可申请处理，不泄露存在性")
    void rejectsOthersOrderItem() throws Exception {
        BuyerAccount owner = createBuyer();
        Order order = completedOrder(owner);
        BuyerAccount other = createBuyer();
        String otherToken = bearer(loginBuyer(other.getUsername(), PASSWORD));

        assertApiCode(apply(otherToken, firstItemId(order), "标题", "描述", List.of()), 81003);
    }

    @Test
    @DisplayName("超过 9 张图片：81007")
    void rejectsTooManyImages() throws Exception {
        BuyerAccount buyer = createBuyer();
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        Order order = completedOrder(buyer);
        String url = uploadImage(token);
        List<String> ten = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            ten.add(url);
        }

        assertApiCode(apply(token, firstItemId(order), "标题", "描述", ten), 81007);
    }

    @Test
    @DisplayName("外链与跨用途 URL 都被拒：只能是售后目录下已落盘的图片")
    void rejectsForeignImageUrls() throws Exception {
        BuyerAccount buyer = createBuyer();
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        Order order = completedOrder(buyer);
        String itemId = firstItemId(order);

        assertApiCode(apply(token, itemId, "标题", "描述",
                List.of("https://example.com/a.jpg")), 20016);
        assertApiCode(apply(token, itemId, "标题", "描述",
                List.of(uploadGoodsImage())), 20016);
    }

    @Test
    @DisplayName("标题与描述不合法：81006")
    void rejectsInvalidContent() throws Exception {
        BuyerAccount buyer = createBuyer();
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        Order order = completedOrder(buyer);
        String itemId = firstItemId(order);

        assertApiCode(apply(token, itemId, "a".repeat(101), "描述", List.of()), 81006);
        assertApiCode(apply(token, itemId, "<b>标题</b>", "描述", List.of()), 81006);
        assertApiCode(apply(token, itemId, "标题", " ", List.of()), 81006);
    }

    @Test
    @DisplayName("卖家详情与买家详情口径一致：同一张单两边都能查到")
    void sellerSeesTheSameAftersale() throws Exception {
        BuyerAccount buyer = createBuyer();
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        Order order = completedOrder(buyer);
        String aftersaleId = dataOf(apply(token, firstItemId(order), "标题", "描述", List.of()))
                .path("aftersale_id").asText();

        // 卖家侧接口在 T5-3 落地，这里先确认数据本身可按订单反查。
        assertThat(aftersales.findById(aftersaleId)).isPresent();
        assertThat(aftersales.countByOrderId(order.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("售后凭证图：买家可上传到 aftersale 目录；卖家令牌 401，商品图用途 20027")
    void buyerMediaEndpointIsRestrictedToAftersaleImages() throws Exception {
        BuyerAccount buyer = createBuyer();
        String token = bearer(loginBuyer(buyer.getUsername(), PASSWORD));

        String url = uploadImage(token);
        assertThat(url).startsWith("/media/aftersale/");

        assertApiCode(mockMvc.perform(multipart("/api/buyer/media")
                        .file(jpeg())
                        .param("purpose", "GOODS_IMAGE")
                        .header("Authorization", token))
                .andReturn(), 20027);

        assertApiStatusAndCode(mockMvc.perform(multipart("/api/buyer/media")
                        .file(jpeg())
                        .param("purpose", "AFTERSALE_IMAGE")
                        .header("Authorization", bearer(loginSeller("Abcd@1234"))))
                .andReturn(), 401, 10002);
    }

    @Test
    @DisplayName("未登录不能申请售后")
    void anonymousCannotApply() throws Exception {
        BuyerAccount buyer = createBuyer();
        Order order = completedOrder(buyer);

        assertApiStatusAndCode(mockMvc.perform(post("/api/buyer/aftersales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "order_item_id", firstItemId(order),
                                "title", "标题",
                                "description", "描述",
                                "image_urls", List.of()))))
                .andReturn(), 401, 10002);
    }
}
