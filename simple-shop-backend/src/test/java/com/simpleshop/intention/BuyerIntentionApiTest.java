package com.simpleshop.intention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;

import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.intention.entity.GoodsQueueSequence;
import com.simpleshop.intention.entity.Intention;
import com.simpleshop.intention.enums.IntentionStatus;
import com.simpleshop.testing.ApiTestBase;

class BuyerIntentionApiTest extends ApiTestBase {

    @Test
    @DisplayName("提交意向成功：回执不含位次，意向排队中、序号为 1")
    void submitsIntentionWithoutQueuePosition() throws Exception {
        Goods goods = publishedGoods();
        String buyer = registerBuyerAndLogin();

        MvcResult result = mockMvc.perform(post("/api/buyer/intentions")
                        .header("Authorization", bearer(loginBuyer(buyer, BUYER_PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("goods_id", goods.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.create_at").isNotEmpty())
                .andExpect(jsonPath("$.data.queue_order").doesNotExist())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Intention intention =
                intentions.findById(body.path("data").path("id").asText()).orElseThrow();
        assertThat(intention.getStatus()).isEqualTo(IntentionStatus.QUEUED);
        assertThat(intention.getQueueOrder()).isEqualTo(1);
        assertThat(intention.getGoodsId()).isEqualTo(goods.getId());
        assertThat(intention.getBuyer().getUsername()).isEqualTo(buyer);
    }

    @Test
    @DisplayName("同一买家可对同一商品重复提交，序号递增")
    void allowsRepeatedSubmissions() throws Exception {
        Goods goods = publishedGoods();
        String token = loginBuyer(registerBuyerAndLogin(), BUYER_PASSWORD);
        String body = json(Map.of("goods_id", goods.getId()));

        mockMvc.perform(post("/api/buyer/intentions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/buyer/intentions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        List<Intention> queued = intentions.findByGoodsIdAndStatusOrderByQueueOrderAsc(
                goods.getId(), IntentionStatus.QUEUED);
        assertThat(queued).hasSize(2);
        assertThat(queued.get(0).getQueueOrder()).isEqualTo(1);
        assertThat(queued.get(1).getQueueOrder()).isEqualTo(2);
        assertThat(queued.get(0).getBuyer().getId()).isEqualTo(queued.get(1).getBuyer().getId());
    }

    @Test
    @DisplayName("未登录提交返回 401")
    void requiresBuyerSession() throws Exception {
        Goods goods = publishedGoods();

        MvcResult result = mockMvc.perform(post("/api/buyer/intentions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("goods_id", goods.getId()))))
                .andReturn();

        assertApiStatusAndCode(result, 401, 10002);
    }

    @Test
    @DisplayName("卖家令牌不能调用买家提交接口")
    void rejectsSellerToken() throws Exception {
        Goods goods = publishedGoods();

        MvcResult result = mockMvc.perform(post("/api/buyer/intentions")
                        .header("Authorization", bearer(loginSeller(SELLER_PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("goods_id", goods.getId()))))
                .andReturn();

        assertApiStatusAndCode(result, 401, 10002);
        assertThat(intentions.count()).isZero();
    }

    @Test
    @DisplayName("商品不存在返回 404，ID 为空返回字段级参数错误")
    void rejectsUnknownOrBlankGoods() throws Exception {
        String token = loginBuyer(registerBuyerAndLogin(), BUYER_PASSWORD);

        MvcResult unknown = mockMvc.perform(post("/api/buyer/intentions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("goods_id", unique("G-missing")))))
                .andReturn();
        assertApiStatusAndCode(unknown, 404, 20011);

        MvcResult blank = mockMvc.perform(post("/api/buyer/intentions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("goods_id", " "))))
                .andReturn();
        assertApiFieldError(blank, "goods_id");
    }

    @Test
    @DisplayName("商品不在售时拒绝提交且不产生意向")
    void rejectsGoodsNotOnSale() throws Exception {
        Goods goods = publishedGoods();
        String token = loginBuyer(registerBuyerAndLogin(), BUYER_PASSWORD);
        String body = json(Map.of("goods_id", goods.getId()));

        for (GoodsStatus status : List.of(
                GoodsStatus.FROZEN, GoodsStatus.OFF_MANUAL, GoodsStatus.OFF_ZERO)) {
            goods.changeStatus(status);
            goodsRepository.saveAndFlush(goods);
            MvcResult result = mockMvc.perform(post("/api/buyer/intentions")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();
            assertApiCode(result, 20019);
        }
        assertThat(intentions.count()).isZero();
    }

    private static final String BUYER_PASSWORD = "Buyer@1234";
    private static final String SELLER_PASSWORD = "Abcd@1234";

    /** 发布服务会初始化队列序号行；测试直建商品时需手动补建。 */
    private Goods publishedGoods() {
        Goods goods = createGoods(createCategory());
        queueSequences.saveAndFlush(GoodsQueueSequence.forGoods(goods.getId()));
        return goods;
    }

    private String registerBuyerAndLogin() throws Exception {
        String username = unique("buyer-");
        registerBuyer(username, BUYER_PASSWORD, "13800000000", "默认地点");
        return username;
    }
}
