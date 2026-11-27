package com.simpleshop.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.testing.ApiTestBase;

/**
 * 地址簿：上限、默认、编辑、删除补位与归属隔离。
 *
 * <p>默认地址只有一个，由数据库唯一索引兜底；这里从接口层面验证「第一条自动默认、切默认、
 * 删默认补位、编辑不动默认」这四条规则。
 */
class AddressApiTest extends ApiTestBase {

    private static final String PASSWORD = "Buyer@1234";

    @Test
    @DisplayName("第一条自动成为默认，第二条不是；列表默认排最前")
    void firstAddressBecomesDefault() throws Exception {
        BuyerAccount buyer = createBuyer();
        String token = tokenOf(buyer);

        JsonNode first = dataOf(create(token, "张三", "13800000000", "北京市朝阳区某路 1 号"));
        JsonNode second = dataOf(create(token, "李四", "13900000000", "北京市海淀区某路 2 号"));

        assertThat(first.path("is_default").asBoolean()).isTrue();
        assertThat(second.path("is_default").asBoolean()).isFalse();

        JsonNode items = dataOf(list(token)).path("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("id").asText()).isEqualTo(first.path("id").asText());
        assertThat(items.get(0).path("is_default").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("设默认：新默认生效，原默认自动取消")
    void switchingDefaultClearsTheOldOne() throws Exception {
        BuyerAccount buyer = createBuyer();
        String token = tokenOf(buyer);
        String firstId = dataOf(create(token, "张三", "13800000000", "北京市朝阳区某路 1 号"))
                .path("id").asText();
        String secondId = dataOf(create(token, "李四", "13900000000", "北京市海淀区某路 2 号"))
                .path("id").asText();

        MvcResult result = mockMvc.perform(
                        post("/api/buyer/addresses/{id}/default", secondId)
                                .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn();
        assertApiCode(result, 0);

        JsonNode items = dataOf(list(token)).path("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("id").asText()).isEqualTo(secondId);
        assertThat(items.get(0).path("is_default").asBoolean()).isTrue();
        assertThat(defaultCount(token)).isEqualTo(1);
        assertThat(addresses.findById(firstId).orElseThrow().isDefault()).isFalse();
    }

    @Test
    @DisplayName("编辑只改三个字段，不动默认标记")
    void updateDoesNotTouchDefaultFlag() throws Exception {
        BuyerAccount buyer = createBuyer();
        String token = tokenOf(buyer);
        String defaultId = dataOf(create(token, "张三", "13800000000", "北京市朝阳区某路 1 号"))
                .path("id").asText();
        String otherId = dataOf(create(token, "李四", "13900000000", "北京市海淀区某路 2 号"))
                .path("id").asText();

        assertApiCode(update(token, defaultId, "张三丰", "13700000000", "北京市朝阳区某路 9 号"), 0);
        assertApiCode(update(token, otherId, "李四光", "13600000000", "北京市海淀区某路 8 号"), 0);

        assertThat(addresses.findById(defaultId).orElseThrow().isDefault()).isTrue();
        assertThat(addresses.findById(defaultId).orElseThrow().getReceiverName())
                .isEqualTo("张三丰");
        assertThat(addresses.findById(otherId).orElseThrow().isDefault()).isFalse();
    }

    @Test
    @DisplayName("删默认地址后由最新一条补位；删光后没有默认")
    void deletingDefaultPromotesTheLatest() throws Exception {
        BuyerAccount buyer = createBuyer();
        String token = tokenOf(buyer);
        String firstId = dataOf(create(token, "张三", "13800000000", "北京市朝阳区某路 1 号"))
                .path("id").asText();
        String secondId = dataOf(create(token, "李四", "13900000000", "北京市海淀区某路 2 号"))
                .path("id").asText();

        assertApiCode(deleteAddress(token, firstId), 0);
        assertThat(addresses.findById(firstId)).isEmpty();
        assertThat(addresses.findById(secondId).orElseThrow().isDefault()).isTrue();

        assertApiCode(deleteAddress(token, secondId), 0);
        assertThat(defaultCount(token)).isZero();
    }

    @Test
    @DisplayName("第 11 条地址被拒：80002")
    void rejectsEleventhAddress() throws Exception {
        BuyerAccount buyer = createBuyer();
        String token = tokenOf(buyer);
        for (int index = 0; index < 10; index++) {
            assertApiCode(create(token, "张三", "13800000000", "北京市朝阳区某路 " + index + " 号"), 0);
        }

        assertApiCode(create(token, "张三", "13800000000", "北京市朝阳区某路 99 号"), 80002);
        assertThat(addresses.countByBuyerId(buyer.getId())).isEqualTo(10);
    }

    @Test
    @DisplayName("他人地址一律按不存在处理")
    void otherBuyersAddressIsInvisible() throws Exception {
        BuyerAccount owner = createBuyer();
        String ownerToken = tokenOf(owner);
        String addressId = dataOf(create(ownerToken, "张三", "13800000000", "北京市朝阳区某路 1 号"))
                .path("id").asText();

        BuyerAccount other = createBuyer();
        String otherToken = tokenOf(other);

        assertApiStatusAndCode(update(otherToken, addressId, "李四", "13900000000", "偷改"),
                404, 80001);
        assertApiStatusAndCode(deleteAddress(otherToken, addressId), 404, 80001);
        assertApiStatusAndCode(mockMvc.perform(
                        post("/api/buyer/addresses/{id}/default", addressId)
                                .header("Authorization", otherToken))
                .andReturn(), 404, 80001);
        assertThat(addresses.findById(addressId).orElseThrow().getReceiverName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("字段不合法：电话太短或带换行返回 80003")
    void rejectsInvalidFields() throws Exception {
        BuyerAccount buyer = createBuyer();
        String token = tokenOf(buyer);

        assertApiCode(create(token, "张三", "12345", "北京市朝阳区某路 1 号"), 80003);
        assertApiCode(create(token, "张三", "13800000000", "第一行\n第二行"), 80003);
        assertApiCode(create(token, "张<三>", "13800000000", "北京市朝阳区某路 1 号"), 80003);
    }

    @Test
    @DisplayName("游客与卖家令牌都不能访问地址簿")
    void requiresBuyerToken() throws Exception {
        mockMvc.perform(get("/api/buyer/addresses")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/buyer/addresses")
                        .header("Authorization", bearer(loginSeller("Abcd@1234"))))
                .andExpect(status().isUnauthorized());
    }

    private String tokenOf(BuyerAccount buyer) throws Exception {
        return bearer(loginBuyer(buyer.getUsername(), PASSWORD));
    }

    private MvcResult create(String token, String name, String phone, String detail)
            throws Exception {
        return mockMvc.perform(post("/api/buyer/addresses")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "receiver_name", name,
                                "receiver_phone", phone,
                                "detail_address", detail))))
                .andReturn();
    }

    private MvcResult update(String token, String id, String name, String phone, String detail)
            throws Exception {
        return mockMvc.perform(put("/api/buyer/addresses/{id}", id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "receiver_name", name,
                                "receiver_phone", phone,
                                "detail_address", detail))))
                .andReturn();
    }

    private MvcResult deleteAddress(String token, String id) throws Exception {
        return mockMvc.perform(delete("/api/buyer/addresses/{id}", id)
                        .header("Authorization", token))
                .andReturn();
    }

    private MvcResult list(String token) throws Exception {
        return mockMvc.perform(get("/api/buyer/addresses")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn();
    }

    private long defaultCount(String token) throws Exception {
        JsonNode items = dataOf(list(token)).path("items");
        long count = 0;
        for (JsonNode item : items) {
            if (item.path("is_default").asBoolean()) {
                count++;
            }
        }
        return count;
    }
}
