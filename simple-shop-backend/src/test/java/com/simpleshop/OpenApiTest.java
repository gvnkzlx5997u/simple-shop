package com.simpleshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import com.simpleshop.testing.ApiTestBase;

class OpenApiTest extends ApiTestBase {

    @Test
    @DisplayName("springdoc 生成 OpenAPI，覆盖全部已实现接口")
    void apiDocsExposeAllImplementedEndpoints() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains(
                // 公共
                "/api/public/health",
                "/api/public/categories",
                "/api/public/goods",
                // 账号
                "/api/buyer/auth/register",
                "/api/buyer/auth/session",
                "/api/seller/auth/session",
                "/api/seller/auth/password",
                // 媒体
                "/api/seller/media",
                // 类别
                "/api/seller/categories/l1",
                "/api/seller/categories/l2",
                // 商品
                "/api/seller/goods",
                "/api/seller/goods/{id}/stock",
                "/api/seller/goods/{id}/off-shelf",
                "/api/seller/goods/{id}/re-list",
                "/api/seller/goods/{id}/category",
                // 意向与交易
                "/api/buyer/intentions",
                "/api/buyer/history",
                "/api/seller/goods/{goodsId}/intentions",
                "/api/seller/intentions/{id}/trade",
                "/api/seller/intentions/{id}/success",
                "/api/seller/intentions/{id}/failure",
                // 历史与客户
                "/api/seller/history/goods",
                "/api/seller/history/goods/{id}",
                "/api/seller/customers",
                "/api/seller/customers/{id}");
    }
}
