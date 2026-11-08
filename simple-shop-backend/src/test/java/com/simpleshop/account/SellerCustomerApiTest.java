package com.simpleshop.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.common.auth.PasswordHasher;
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
    @DisplayName("客户详情返回基本信息，购买历史待订单模块提供")
    void detailReturnsCustomerBasics() throws Exception {
        BuyerAccount buyer = createBuyer();

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
        assertThat(data.has("purchase_history")).isFalse();
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
}
