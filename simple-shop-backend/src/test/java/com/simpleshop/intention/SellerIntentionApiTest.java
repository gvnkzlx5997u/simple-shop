package com.simpleshop.intention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.intention.entity.GoodsQueueSequence;
import com.simpleshop.intention.entity.Intention;
import com.simpleshop.intention.enums.FailType;
import com.simpleshop.testing.ApiTestBase;

class SellerIntentionApiTest extends ApiTestBase {

    @Test
    @DisplayName("意向名单按队列序号升序，包含买家联系方式")
    void listsIntentionsByQueueOrder() throws Exception {
        Goods goods = publishedGoods();
        BuyerAccount first = createBuyer();
        BuyerAccount second = createBuyer();
        submit(goods, first, 1);
        submit(goods, second, 2);

        MvcResult result = mockMvc.perform(get("/api/seller/goods/{id}/intentions", goods.getId())
                        .header("Authorization", bearer(loginSeller("Abcd@1234"))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = itemsOf(result);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("queue_order").asLong()).isEqualTo(1);
        assertThat(items.get(0).path("buyer_username").asText()).isEqualTo(first.getUsername());
        assertThat(items.get(0).path("buyer_phone").asText()).isEqualTo(first.getPhone());
        assertThat(items.get(0).path("buyer_default_location").asText())
                .isEqualTo(first.getDefaultLocation());
        assertThat(items.get(0).path("status").asText()).isEqualTo("QUEUED");
        assertThat(items.get(0).path("fail_type").isNull()).isTrue();
        assertThat(items.get(0).path("finish_at").isNull()).isTrue();
        assertThat(items.get(0).path("create_at").asText()).endsWith("Z");
        assertThat(items.get(1).path("buyer_username").asText()).isEqualTo(second.getUsername());
    }

    @Test
    @DisplayName("意向名单支持状态筛选与分页")
    void filtersByStatusAndPaginates() throws Exception {
        Goods goods = publishedGoods();
        BuyerAccount buyer = createBuyer();
        Intention first = submit(goods, buyer, 1);
        Intention second = submit(goods, buyer, 2);
        Intention third = submit(goods, buyer, 3);
        first.markFailed(FailType.SELLER_VOIDED);
        third.markFailed(FailType.SELLER_VOIDED);
        intentions.saveAllAndFlush(List.of(first, third));
        String token = bearer(loginSeller("Abcd@1234"));

        MvcResult failedPage = mockMvc.perform(get("/api/seller/goods/{id}/intentions", goods.getId())
                        .header("Authorization", token)
                        .queryParam("status", "FAILED")
                        .queryParam("page", "1")
                        .queryParam("page_size", "1"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode failed = itemsOf(failedPage);
        assertThat(failed).hasSize(1);
        assertThat(failed.get(0).path("id").asText()).isEqualTo(first.getId());
        assertThat(dataOf(failedPage).path("total").asLong()).isEqualTo(2);
        assertThat(failed.get(0).path("fail_type").asText()).isEqualTo("SELLER_VOIDED");
        assertThat(failed.get(0).path("finish_at").asText()).isNotEmpty();

        MvcResult secondPage = mockMvc.perform(get("/api/seller/goods/{id}/intentions", goods.getId())
                        .header("Authorization", token)
                        .queryParam("status", "QUEUED")
                        .queryParam("page", "1")
                        .queryParam("page_size", "1"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(itemsOf(secondPage)).hasSize(1);
        assertThat(itemsOf(secondPage).get(0).path("id").asText()).isEqualTo(second.getId());
    }

    @Test
    @DisplayName("空队列返回空列表；下架商品仍可查看意向")
    void allowsEmptyQueueAndOffShelfGoods() throws Exception {
        Goods goods = publishedGoods();

        MvcResult empty = mockMvc.perform(get("/api/seller/goods/{id}/intentions", goods.getId())
                        .header("Authorization", bearer(loginSeller("Abcd@1234"))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(itemsOf(empty)).isEmpty();
        assertThat(dataOf(empty).path("total").asLong()).isZero();

        goods.changeStatus(GoodsStatus.OFF_MANUAL);
        goodsRepository.saveAndFlush(goods);
        mockMvc.perform(get("/api/seller/goods/{id}/intentions", goods.getId())
                        .header("Authorization", bearer(loginSeller("Abcd@1234"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("商品不存在返回 404；非法状态参数返回 400")
    void rejectsUnknownGoodsAndInvalidStatus() throws Exception {
        String token = bearer(loginSeller("Abcd@1234"));

        MvcResult unknown = mockMvc.perform(get("/api/seller/goods/{id}/intentions", unique("G-x"))
                        .header("Authorization", token))
                .andReturn();
        assertApiStatusAndCode(unknown, 404, 20011);

        Goods goods = publishedGoods();
        MvcResult invalid = mockMvc.perform(get("/api/seller/goods/{id}/intentions", goods.getId())
                        .header("Authorization", token)
                        .queryParam("status", "PENDING"))
                .andReturn();
        assertApiStatusAndCode(invalid, 400, 50002);
    }

    @Test
    @DisplayName("意向名单需要卖家令牌")
    void requiresSellerSession() throws Exception {
        Goods goods = publishedGoods();
        String buyerToken = buyerToken();

        MvcResult anonymous = mockMvc.perform(get("/api/seller/goods/{id}/intentions", goods.getId()))
                .andReturn();
        assertApiStatusAndCode(anonymous, 401, 10002);

        MvcResult buyerCall = mockMvc
                .perform(get("/api/seller/goods/{id}/intentions", goods.getId())
                        .header("Authorization", bearer(buyerToken)))
                .andReturn();
        assertApiStatusAndCode(buyerCall, 401, 10002);
    }

    private Intention submit(Goods goods, BuyerAccount buyer, long queueOrder) {
        return intentions.saveAndFlush(Intention.submit(goods, buyer, queueOrder));
    }

    private String buyerToken() throws Exception {
        String username = unique("buyer-");
        registerBuyer(username, "Buyer@1234", "13800000000", null);
        return loginBuyer(username, "Buyer@1234");
    }

    private Goods publishedGoods() {
        Goods goods = createGoods(createCategory());
        queueSequences.saveAndFlush(GoodsQueueSequence.forGoods(goods.getId()));
        return goods;
    }

    private JsonNode itemsOf(MvcResult result) throws Exception {
        return dataOf(result).path("items");
    }
}
