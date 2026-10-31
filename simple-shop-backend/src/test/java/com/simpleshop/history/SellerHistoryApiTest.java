package com.simpleshop.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;

import com.simpleshop.common.time.DatabaseTimeProvider;
import com.simpleshop.history.entity.GoodsHistory;
import com.simpleshop.history.entity.IntentionHistory;
import com.simpleshop.history.enums.IntentionResult;
import com.simpleshop.history.enums.FailType;
import com.simpleshop.testing.ApiTestBase;

class SellerHistoryApiTest extends ApiTestBase {

    @Test
    @DisplayName("历史商品列表分页返回快照字段")
    void listsHistoryGoodsWithSnapshotFields() throws Exception {
        archivedGoods("历史商品甲", "数码", "手机");
        archivedGoods("历史商品乙", "服饰", "上衣");
        String token = loginSeller("Abcd@1234");

        MvcResult result = mockMvc.perform(get("/api/seller/history/goods")
                        .header("Authorization", bearer(token))
                        .queryParam("page", "1")
                        .queryParam("page_size", "1"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = dataOf(result);
        assertThat(data.path("total").asLong()).isEqualTo(2);
        assertThat(data.path("items")).hasSize(1);
        JsonNode item = data.path("items").get(0);
        assertThat(item.path("name").asText()).startsWith("历史商品");
        assertThat(item.path("price").asText()).isEqualTo("99.99");
        assertThat(item.path("category_l1_name").asText()).isNotEmpty();
        assertThat(item.path("category_l2_name").asText()).isNotEmpty();
        assertThat(item.path("result").asText()).isEqualTo("SOLD_OUT");
        assertThat(item.path("archived_at").asText()).endsWith("Z");
    }

    @Test
    @DisplayName("历史商品详情含描述与图片快照及全部历史意向")
    void detailShowsSnapshotsAndIntentionRoster() throws Exception {
        GoodsHistory goods = archivedGoods("历史商品丙", "数码", "手机",
                "[{\"type\":\"TEXT\",\"text\":\"首块文本\",\"url\":null},"
                        + "{\"type\":\"TEXT\",\"text\":\"描述文本\",\"url\":null},"
                        + "{\"type\":\"VIDEO\",\"text\":null,\"url\":\"/media/description/v.mp4\"}]",
                "[{\"url\":\"/media/goods/main.jpg\",\"is_main\":true,\"sort_no\":1},"
                        + "{\"url\":\"/media/goods/g1.jpg\",\"is_main\":false,\"sort_no\":2}]");
        archivedIntention(goods, "buyer-one", "13811112222",
                IntentionResult.SUCCEEDED, null, 30);
        archivedIntention(goods, "buyer-two", "13933334444",
                IntentionResult.FAILED, FailType.SOLD_OUT, 20);

        MvcResult result = mockMvc.perform(get("/api/seller/history/goods/{id}", goods.getId())
                        .header("Authorization", bearer(loginSeller("Abcd@1234"))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = dataOf(result);
        assertThat(data.path("name").asText()).isEqualTo("历史商品丙");
        assertThat(data.path("category_l1_name").asText()).isEqualTo("数码");
        assertThat(data.path("category_l2_name").asText()).isEqualTo("手机");
        assertThat(data.path("result").asText()).isEqualTo("SOLD_OUT");

        JsonNode description = data.path("description");
        assertThat(description).hasSize(3);
        assertThat(description.get(1).path("type").asText()).isEqualTo("TEXT");
        assertThat(description.get(1).path("text").asText()).isEqualTo("描述文本");
        assertThat(description.get(2).path("url").asText()).isEqualTo("/media/description/v.mp4");

        JsonNode images = data.path("images");
        assertThat(images).hasSize(2);
        assertThat(images.get(0).path("is_main").asBoolean()).isTrue();
        assertThat(images.get(0).path("sort_no").asInt()).isEqualTo(1);

        JsonNode intentions = data.path("intentions");
        assertThat(intentions).hasSize(2);
        JsonNode first = intentions.get(0);
        assertThat(first.path("buyer_username").asText()).isEqualTo("buyer-one");
        assertThat(first.path("buyer_phone").asText()).isEqualTo("13811112222");
        assertThat(first.path("result").asText()).isEqualTo("SUCCEEDED");
        assertThat(first.path("fail_type").isNull()).isTrue();
        assertThat(first.path("finish_at").asText()).isNotEmpty();
        JsonNode second = intentions.get(1);
        assertThat(second.path("result").asText()).isEqualTo("FAILED");
        assertThat(second.path("fail_type").asText()).isEqualTo("SOLD_OUT");
    }

    @Test
    @DisplayName("不存在的历史商品返回 404")
    void rejectsUnknownHistoryGoods() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/seller/history/goods/{id}", unique("G-x"))
                        .header("Authorization", bearer(loginSeller("Abcd@1234"))))
                .andReturn();

        assertApiStatusAndCode(result, 404, 40001);
    }

    @Test
    @DisplayName("历史查询需要卖家令牌")
    void requiresSellerSession() throws Exception {
        MvcResult anonymous = mockMvc.perform(get("/api/seller/history/goods")).andReturn();
        assertApiStatusAndCode(anonymous, 401, 10002);

        String username = unique("buyer-");
        registerBuyer(username, "Buyer@1234", "13800000000", null);
        String buyerToken = loginBuyer(username, "Buyer@1234");
        MvcResult buyer = mockMvc.perform(get("/api/seller/history/goods")
                        .header("Authorization", bearer(buyerToken)))
                .andReturn();
        assertApiStatusAndCode(buyer, 401, 10002);
    }

    private GoodsHistory archivedGoods(String name, String l1Name, String l2Name) {
        return archivedGoods(name, l1Name, l2Name, "[]", "[]");
    }

    private GoodsHistory archivedGoods(
            String name, String l1Name, String l2Name,
            String descriptionSnapshot, String imagesSnapshot) {
        LocalDateTime now = DatabaseTimeProvider.utcNow();
        return goodsHistories.saveAndFlush(GoodsHistory.archive(
                "G" + UUID.randomUUID(),
                name,
                new BigDecimal("99.99"),
                0,
                l1Name,
                l2Name,
                descriptionSnapshot,
                imagesSnapshot,
                now.minusHours(1)));
    }

    private void archivedIntention(
            GoodsHistory goods, String username, String phone,
            IntentionResult result, FailType failType, int minutesAgo) {
        LocalDateTime now = DatabaseTimeProvider.utcNow();
        intentionHistories.saveAndFlush(IntentionHistory.archive(
                "I" + UUID.randomUUID(),
                goods,
                goods.getName(),
                unique("BA-"),
                username,
                phone,
                now.minusMinutes(minutesAgo),
                result,
                failType,
                now.minusMinutes(minutesAgo - 5)));
    }
}
