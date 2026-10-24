package com.simpleshop.intention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.intention.entity.GoodsQueueSequence;
import com.simpleshop.intention.entity.Intention;
import com.simpleshop.intention.enums.FailType;
import com.simpleshop.testing.ApiTestBase;

class BuyerHistoryApiTest extends ApiTestBase {

    @Test
    @DisplayName("买家历史只含自己的终态意向，字段不含价格与排队信息")
    void showsOnlyOwnTerminalIntentions() throws Exception {
        BuyerAccount buyer = createBuyer();
        Goods firstGoods = publishedGoods();
        Intention succeeded = terminalIntention(firstGoods, buyer, 1);
        Goods secondGoods = publishedGoods();
        intentions.saveAndFlush(Intention.submit(secondGoods, buyer, 1));
        BuyerAccount other = createBuyer();
        Intention failedByOther = intentions.saveAndFlush(
                Intention.submit(secondGoods, other, 2));
        failedByOther.markFailed(FailType.SELLER_VOIDED);
        intentions.saveAndFlush(failedByOther);
        String token = loginBuyer(buyer.getUsername(), "Buyer@1234");

        MvcResult result = history(token, null, null);

        JsonNode data = dataOf(result);
        assertThat(data.path("total").asLong()).isEqualTo(1);
        JsonNode item = data.path("items").get(0);
        assertThat(item.path("goods_name").asText()).isEqualTo(firstGoods.getName());
        assertThat(item.path("result").asText()).isEqualTo("SUCCEEDED");
        assertThat(item.path("create_at").asText()).endsWith("Z");
        assertThat(item.path("finish_at").asText()).isNotEmpty();
        assertThat(item.has("price")).isFalse();
        assertThat(item.has("queue_order")).isFalse();
        assertThat(item.has("id")).isFalse();
    }

    @Test
    @DisplayName("历史并集当前表终态与归档后的历史意向")
    void unionsCurrentAndArchivedIntentions() throws Exception {
        Goods soldOut = publishedGoods();
        soldOut.changeStock(1);
        goodsRepository.saveAndFlush(soldOut);
        BuyerAccount buyer = createBuyer();
        Intention archived = enterTrade(soldOut, submit(soldOut, buyer, 1));
        String sellerToken = loginSeller("Abcd@1234");
        mockMvc.perform(post("/api/seller/intentions/{id}/success", archived.getId())
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        flushAndClear();
        Goods active = publishedGoods();
        terminalIntention(active, buyer, 1);
        String token = loginBuyer(buyer.getUsername(), "Buyer@1234");

        MvcResult result = history(token, null, null);

        JsonNode data = dataOf(result);
        assertThat(data.path("total").asLong()).isEqualTo(2);
        assertThat(data.path("items")).hasSize(2);
        assertThat(data.path("items").toString())
                .contains(soldOut.getName())
                .contains(active.getName());
    }

    @Test
    @DisplayName("买家历史分页在并集之后")
    void paginatesAfterUnion() throws Exception {
        BuyerAccount buyer = createBuyer();
        Goods goods = publishedGoods();
        for (int order = 1; order <= 3; order++) {
            terminalIntention(goods, buyer, order);
        }
        String token = loginBuyer(buyer.getUsername(), "Buyer@1234");

        MvcResult firstPage = history(token, "1", "2");
        assertThat(dataOf(firstPage).path("total").asLong()).isEqualTo(3);
        assertThat(dataOf(firstPage).path("items")).hasSize(2);

        MvcResult secondPage = history(token, "2", "2");
        assertThat(dataOf(secondPage).path("items")).hasSize(1);
    }

    @Test
    @DisplayName("新买家历史为空")
    void emptyHistoryForNewBuyer() throws Exception {
        String username = unique("buyer-");
        registerBuyer(username, "Buyer@1234", "13800000000", null);

        MvcResult result = history(loginBuyer(username, "Buyer@1234"), null, null);

        assertThat(dataOf(result).path("total").asLong()).isZero();
        assertThat(dataOf(result).path("items").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("买家历史需要买家令牌")
    void requiresBuyerSession() throws Exception {
        MvcResult anonymous = history(null, null, null);
        assertApiStatusAndCode(anonymous, 401, 10002);

        MvcResult seller = history(loginSeller("Abcd@1234"), null, null);
        assertApiStatusAndCode(seller, 401, 10002);
    }

    private MvcResult history(String token, String page, String pageSize) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/buyer/history");
        if (token != null) {
            request.header("Authorization", bearer(token));
        }
        if (page != null) {
            request.queryParam("page", page);
        }
        if (pageSize != null) {
            request.queryParam("page_size", pageSize);
        }
        return mockMvc.perform(request).andReturn();
    }

    private Goods publishedGoods() {
        Goods goods = createGoods(createCategory());
        queueSequences.saveAndFlush(GoodsQueueSequence.forGoods(goods.getId()));
        return goods;
    }

    private Intention submit(Goods goods, BuyerAccount buyer, long queueOrder) {
        return intentions.saveAndFlush(Intention.submit(goods, buyer, queueOrder));
    }

    private Intention terminalIntention(Goods goods, BuyerAccount buyer, long queueOrder) {
        Intention intention = submit(goods, buyer, queueOrder);
        intention.markSucceeded();
        return intentions.saveAndFlush(intention);
    }

    private Intention enterTrade(Goods goods, Intention intention) {
        intention.enterTrade();
        goods.changeStatus(GoodsStatus.FROZEN);
        intentions.saveAndFlush(intention);
        goodsRepository.saveAndFlush(goods);
        return intention;
    }
}
