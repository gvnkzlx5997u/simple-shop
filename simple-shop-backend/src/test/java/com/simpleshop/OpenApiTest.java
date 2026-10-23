package com.simpleshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class OpenApiTest extends ApiTestBase {

    @Test
    @DisplayName("springdoc 生成 OpenAPI，包含账号、类别、商品与媒体接口")
    void apiDocsExposeAccountCategoryGoodsAndMediaEndpoints() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains(
                "/api/buyer/auth/register",
                "/api/buyer/auth/session",
                "/api/seller/auth/session",
                "/api/seller/auth/password",
                "/api/seller/media",
                "/api/public/categories",
                "/api/seller/categories/l1",
                "/api/seller/categories/l2",
                "/api/public/goods",
                "/api/seller/goods",
                "/api/seller/goods/{id}/stock",
                "/api/seller/goods/{id}/off-shelf",
                "/api/seller/goods/{id}/re-list",
                "/api/seller/goods/{id}/category");
    }
}
