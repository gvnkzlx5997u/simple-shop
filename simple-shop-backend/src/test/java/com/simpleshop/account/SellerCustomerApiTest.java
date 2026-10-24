package com.simpleshop.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.common.auth.PasswordHasher;
import com.simpleshop.common.time.DatabaseTimeProvider;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.history.entity.GoodsHistory;
import com.simpleshop.history.entity.IntentionHistory;
import com.simpleshop.history.enums.IntentionResult;
import com.simpleshop.intention.entity.Intention;
import com.simpleshop.intention.enums.FailType;
import com.simpleshop.testing.ApiTestBase;

class SellerCustomerApiTest extends ApiTestBase {

    @Test
    @DisplayName("客户列表分页返回交易所需字段")
    void listsCustomersWithRequiredFields() throws Exception {
        createBuyer();
        BuyerAccount located = buyerAccounts.saveAndFlush(BuyerAccount.register(
                unique("buyer-"), PasswordHasher.hash("Other@1234"),
                "13911112222", "线下交易点"));
        BuyerAccount noLocation = buyerAccounts.saveAndFlush(BuyerAccount.register(
                unique("buyer-"), PasswordHasher.hash("Other@1234"),
                "13711112222", null));
        String token = bearer(loginSeller("Abcd@1234"));

        MvcResult firstPage = mockMvc.perform(get("/api/seller/customers")
                        .header("Authorization", token)
                        .queryParam("page", "1")
                        .queryParam("page_size", "2"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = dataOf(firstPage);
        assertThat(data.path("total").asLong()).isEqualTo(3);
        assertThat(data.path("items")).hasSize(2);
        JsonNode item = data.path("items").get(0);
        assertThat(item.has("id")).isTrue();
        assertThat(item.path("username").asText()).startsWith("buyer-");
        assertThat(item.path("phone").asText()).isNotEmpty();
        assertThat(item.path("create_at").asText()).endsWith("Z");

        MvcResult secondPage = mockMvc.perform(get("/api/seller/customers")
                        .header("Authorization", token)
                        .queryParam("page", "2")
                        .queryParam("page_size", "2"))
                .andExpect(status().isOk())
                .andReturn();
        String usernames = data.path("items").toString()
                + dataOf(secondPage).path("items").toString();
        assertThat(usernames)
                .contains(located.getUsername())
                .contains(noLocation.getUsername())
                .contains("线下交易点");
    }

    @Test
    @DisplayName("客户列表不提供搜索或筛选参数")
    void ignoresUnsupportedFilters() throws Exception {
        createBuyer();

        MvcResult result = mockMvc.perform(get("/api/seller/customers")
                        .header("Authorization", bearer(loginSeller("Abcd@1234")))
                        .queryParam("keyword", "buyer"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(dataOf(result).path("total").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("客户详情返回基本信息与并集购买历史")
    void detailShowsCustomerAndPurchaseHistory() throws Exception {
        BuyerAccount buyer = createBuyer();
        Goods activeGoods = createGoods(createCategory());
        Intention current = intentions.saveAndFlush(
                Intention.submit(activeGoods, buyer, 1));
        current.markSucceeded();
        intentions.saveAndFlush(current);
        GoodsHistory soldOut = goodsHistories.saveAndFlush(GoodsHistory.archive(
                unique("G"), "归档商品", new BigDecimal("59.00"), 0,
                "数码", "手机", "[]", "[]",
                DatabaseTimeProvider.utcNow().minusHours(2)));
        intentionHistories.saveAndFlush(IntentionHistory.archive(
                unique("I"), soldOut, soldOut.getName(), buyer.getId(),
                buyer.getUsername(), buyer.getPhone(),
                DatabaseTimeProvider.utcNow().minusMinutes(30),
                IntentionResult.FAILED, FailType.SOLD_OUT,
                DatabaseTimeProvider.utcNow().minusMinutes(25)));

        MvcResult result = mockMvc.perform(get("/api/seller/customers/{id}", buyer.getId())
                        .header("Authorization", bearer(loginSeller("Abcd@1234"))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = dataOf(result);
        assertThat(data.path("id").asText()).isEqualTo(buyer.getId());
        assertThat(data.path("username").asText()).isEqualTo(buyer.getUsername());
        assertThat(data.path("phone").asText()).isEqualTo(buyer.getPhone());
        assertThat(data.path("default_location").asText()).isEqualTo("默认地点");
        assertThat(data.path("create_at").asText()).endsWith("Z");

        JsonNode history = data.path("purchase_history");
        assertThat(history.path("total").asLong()).isEqualTo(2);
        assertThat(history.path("items")).hasSize(2);
        String items = history.path("items").toString();
        assertThat(items).contains(activeGoods.getName()).contains("归档商品");
        JsonNode failedEntry = history.path("items").iterator().hasNext()
                ? findEntry(history.path("items"), "归档商品") : null;
        assertThat(failedEntry).isNotNull();
        assertThat(failedEntry.path("result").asText()).isEqualTo("FAILED");
        assertThat(failedEntry.path("fail_type").asText()).isEqualTo("SOLD_OUT");
        assertThat(failedEntry.path("goods_id").asText()).isEqualTo(soldOut.getId());
        JsonNode succeededEntry = findEntry(history.path("items"), activeGoods.getName());
        assertThat(succeededEntry.path("result").asText()).isEqualTo("SUCCEEDED");
        assertThat(succeededEntry.path("fail_type").isNull()).isTrue();
        assertThat(succeededEntry.path("goods_id").asText()).isEqualTo(activeGoods.getId());
    }

    @Test
    @DisplayName("购买历史分页作用于并集之后")
    void paginatesPurchaseHistoryAfterUnion() throws Exception {
        BuyerAccount buyer = createBuyer();
        Goods goods = createGoods(createCategory());
        for (int order = 1; order <= 2; order++) {
            Intention intention = intentions.saveAndFlush(
                    Intention.submit(goods, buyer, order));
            intention.markSucceeded();
            intentions.saveAndFlush(intention);
        }
        intentionHistories.saveAllAndFlush(List.of(archivedIntentionOf(buyer)));

        MvcResult result = mockMvc.perform(get("/api/seller/customers/{id}", buyer.getId())
                        .header("Authorization", bearer(loginSeller("Abcd@1234")))
                        .queryParam("page", "1")
                        .queryParam("page_size", "2"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode history = dataOf(result).path("purchase_history");
        assertThat(history.path("total").asLong()).isEqualTo(3);
        assertThat(history.path("items")).hasSize(2);
    }

    @Test
    @DisplayName("客户不存在返回 404")
    void rejectsUnknownCustomer() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/seller/customers/{id}", unique("BA-x"))
                        .header("Authorization", bearer(loginSeller("Abcd@1234"))))
                .andReturn();

        assertApiStatusAndCode(result, 404, 10009);
    }

    @Test
    @DisplayName("客户查询需要卖家令牌")
    void requiresSellerSession() throws Exception {
        MvcResult anonymous = mockMvc.perform(get("/api/seller/customers")).andReturn();
        assertApiStatusAndCode(anonymous, 401, 10002);

        String username = unique("buyer-");
        registerBuyer(username, "Buyer@1234", "13800000000", null);
        String buyerToken = loginBuyer(username, "Buyer@1234");
        MvcResult buyer = mockMvc.perform(get("/api/seller/customers")
                        .header("Authorization", bearer(buyerToken)))
                .andReturn();
        assertApiStatusAndCode(buyer, 401, 10002);

        MvcResult buyerDetail = mockMvc.perform(get("/api/seller/customers/{id}", unique("BA-x"))
                        .header("Authorization", bearer(buyerToken)))
                .andReturn();
        assertApiStatusAndCode(buyerDetail, 401, 10002);
    }

    private static JsonNode findEntry(JsonNode items, String goodsName) {
        for (JsonNode item : items) {
            if (goodsName.equals(item.path("goods_name").asText())) {
                return item;
            }
        }
        return null;
    }

    private IntentionHistory archivedIntentionOf(BuyerAccount buyer) {
        GoodsHistory soldOut = goodsHistories.saveAndFlush(GoodsHistory.archive(
                unique("G"), "历史商品", new BigDecimal("19.00"), 0,
                "服饰", "上衣", "[]", "[]",
                DatabaseTimeProvider.utcNow().minusHours(1)));
        return IntentionHistory.archive(
                unique("I"), soldOut, soldOut.getName(), buyer.getId(),
                buyer.getUsername(), buyer.getPhone(),
                DatabaseTimeProvider.utcNow().minusMinutes(10),
                IntentionResult.SUCCEEDED, null,
                DatabaseTimeProvider.utcNow().minusMinutes(5));
    }
}
