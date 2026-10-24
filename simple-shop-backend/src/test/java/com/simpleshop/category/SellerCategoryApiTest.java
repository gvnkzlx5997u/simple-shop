package com.simpleshop.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.testing.ApiTestBase;

class SellerCategoryApiTest extends ApiTestBase {

    @Test
    @DisplayName("卖家可以新增、重命名两级类别，并查看类别树")
    void createsRenamesAndListsCategories() throws Exception {
        String token = loginSeller("Abcd@1234");
        String l1Name = unique("一级-");
        MvcResult l1Result = createL1(token, l1Name);
        assertApiCode(l1Result, 0);
        String l1Id = dataId(l1Result);
        assertThat(data(l1Result).path("parent_id").isNull()).isTrue();

        String l2Name = unique("二级-");
        MvcResult l2Result = createL2(token, l1Id, l2Name);
        assertApiCode(l2Result, 0);
        String l2Id = dataId(l2Result);
        assertThat(data(l2Result).path("parent_id").asText()).isEqualTo(l1Id);

        MvcResult tree = mockMvc.perform(get("/api/seller/categories")
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(tree, 0);
        JsonNode l1Node = findById(data(tree), l1Id);
        assertThat(l1Node.path("name").asText()).isEqualTo(l1Name);
        assertThat(findById(l1Node.path("children"), l2Id).path("name").asText())
                .isEqualTo(l2Name);

        MvcResult publicTree = mockMvc.perform(get("/api/public/categories")).andReturn();
        assertApiCode(publicTree, 0);
        assertThat(findById(data(publicTree), l1Id).path("name").asText()).isEqualTo(l1Name);

        String renamedL1 = unique("改名一级-");
        assertApiCode(renameL1(token, l1Id, renamedL1), 0);
        String renamedL2 = unique("改名二级-");
        assertApiCode(renameL2(token, l2Id, renamedL2), 0);

        MvcResult renamedTree = mockMvc.perform(get("/api/public/categories")).andReturn();
        JsonNode renamedL1Node = findById(data(renamedTree), l1Id);
        assertThat(renamedL1Node.path("name").asText()).isEqualTo(renamedL1);
        assertThat(findById(renamedL1Node.path("children"), l2Id).path("name").asText())
                .isEqualTo(renamedL2);
    }

    @Test
    @DisplayName("同名一级类别、同一父级下的同名二级类别被拒绝")
    void rejectsDuplicateCategoryNames() throws Exception {
        String token = loginSeller("Abcd@1234");
        String l1Name = unique("重复一级-");
        String l1Id = dataId(createL1(token, l1Name));

        assertApiCode(createL1(token, l1Name), 20004);

        String l2Name = unique("重复二级-");
        assertApiCode(createL2(token, l1Id, l2Name), 0);
        assertApiCode(createL2(token, l1Id, l2Name), 20004);

        String otherL1Id = dataId(createL1(token, unique("另一个一级-")));
        assertApiCode(createL2(token, otherL1Id, l2Name), 0);

        assertApiCode(renameL1(token, otherL1Id, l1Name), 20004);

        String otherL2Name = unique("另一个二级-");
        String otherL2Id = dataId(createL2(token, otherL1Id, otherL2Name));
        assertApiCode(renameL2(token, otherL2Id, l2Name), 20004);
    }

    @Test
    @DisplayName("一级类别仍有二级子类时不能删除")
    void rejectsDeletingL1WithChildren() throws Exception {
        String token = loginSeller("Abcd@1234");
        String l1Id = dataId(createL1(token, unique("有子类-")));
        assertApiCode(createL2(token, l1Id, unique("子类-")), 0);

        MvcResult result = mockMvc.perform(delete("/api/seller/categories/l1/{id}", l1Id)
                        .header("Authorization", bearer(token)))
                .andReturn();

        assertApiStatusAndCode(result, 200, 20005);
    }

    @Test
    @DisplayName("二级类别被当前商品引用时不能删除")
    void rejectsDeletingL2ReferencedByGoods() throws Exception {
        String token = loginSeller("Abcd@1234");
        CategoryL2 category = createCategory();
        createGoods(category);

        MvcResult result = mockMvc.perform(delete("/api/seller/categories/l2/{id}", category.getId())
                        .header("Authorization", bearer(token)))
                .andReturn();

        assertApiStatusAndCode(result, 200, 20006);
    }

    @Test
    @DisplayName("空的二级、一级类别可以依次删除")
    void deletesEmptyCategories() throws Exception {
        String token = loginSeller("Abcd@1234");
        String l1Id = dataId(createL1(token, unique("待删一级-")));
        String l2Id = dataId(createL2(token, l1Id, unique("待删二级-")));

        assertApiCode(deleteL2(token, l2Id), 0);
        assertApiCode(deleteL1(token, l1Id), 0);
    }

    @Test
    @DisplayName("类别名非法、父级不存在时返回对应业务码")
    void rejectsInvalidCategoryInput() throws Exception {
        String token = loginSeller("Abcd@1234");

        assertApiCode(createL1(token, "  "), 20003);
        assertApiCode(createL1(token, "类".repeat(51)), 20003);
        assertApiCode(createL1(token, "<类别>"), 20003);
        assertApiStatusAndCode(createL2(token, "C1-not-exist", unique("二级-")), 404, 20001);
        assertApiStatusAndCode(renameL1(token, "C1-not-exist", unique("改名-")), 404, 20001);
        assertApiStatusAndCode(deleteL2(token, "C2-not-exist"), 404, 20001);
    }

    @Test
    @DisplayName("类别管理只接受卖家令牌；公共类别树不需要登录")
    void categoryManagementRequiresSellerRole() throws Exception {
        String l1Id = dataId(postJson("/api/seller/categories/l1",
                loginSeller("Abcd@1234"), Map.of("name", unique("鉴权一级-"))));

        MvcResult anonymous = mockMvc.perform(post("/api/seller/categories/l1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "匿名"))))
                .andReturn();
        assertApiStatusAndCode(anonymous, 401, 10002);

        String buyer = unique("buyer-");
        registerBuyer(buyer, "Buyer@1234", "13800000000", null);
        String buyerToken = loginBuyer(buyer, "Buyer@1234");
        MvcResult buyerAttempt = mockMvc.perform(post("/api/seller/categories/l1")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "买家"))))
                .andReturn();
        assertApiStatusAndCode(buyerAttempt, 401, 10002);

        MvcResult publicTree = mockMvc.perform(get("/api/public/categories")).andReturn();
        assertApiCode(publicTree, 0);
        assertThat(findById(data(publicTree), l1Id)).isNotNull();
    }

    private MvcResult createL1(String token, String name) throws Exception {
        return postJson("/api/seller/categories/l1", token, Map.of("name", name));
    }

    private MvcResult createL2(String token, String l1Id, String name) throws Exception {
        return postJson("/api/seller/categories/l2", token,
                Map.of("l1_id", l1Id, "name", name));
    }

    private MvcResult renameL1(String token, String id, String name) throws Exception {
        return putJson("/api/seller/categories/l1/" + id, token, Map.of("name", name));
    }

    private MvcResult renameL2(String token, String id, String name) throws Exception {
        return putJson("/api/seller/categories/l2/" + id, token, Map.of("name", name));
    }

    private MvcResult deleteL1(String token, String id) throws Exception {
        return mockMvc.perform(delete("/api/seller/categories/l1/{id}", id)
                        .header("Authorization", bearer(token)))
                .andReturn();
    }

    private MvcResult deleteL2(String token, String id) throws Exception {
        return mockMvc.perform(delete("/api/seller/categories/l2/{id}", id)
                        .header("Authorization", bearer(token)))
                .andReturn();
    }

    private MvcResult postJson(String path, String token, Object body) throws Exception {
        return mockMvc.perform(post(path)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    private MvcResult putJson(String path, String token, Object body) throws Exception {
        return mockMvc.perform(put(path)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String dataId(MvcResult result) throws Exception {
        return data(result).path("id").asText();
    }

    private JsonNode findById(JsonNode array, String id) {
        for (JsonNode node : array) {
            if (id.equals(node.path("id").asText())) {
                return node;
            }
        }
        throw new AssertionError("category not found in tree: " + id);
    }
}
