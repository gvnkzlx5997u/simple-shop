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
    @DisplayName("springdoc 生成 OpenAPI，覆盖全部已实现接口，且不含已下线端点")
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
                "/api/seller/goods/{id}/category",
                // 客户
                "/api/seller/customers",
                "/api/seller/customers/{id}",
                // 订单
                "/api/buyer/orders",
                "/api/buyer/orders/{id}",
                "/api/buyer/orders/{id}/items",
                "/api/buyer/orders/{id}/cancel",
                "/api/seller/orders",
                "/api/seller/orders/{id}",
                "/api/seller/orders/{id}/items",
                "/api/seller/orders/{id}/confirm",
                "/api/seller/orders/{id}/prepare",
                "/api/seller/orders/{id}/ship",
                "/api/seller/orders/{id}/complete",
                "/api/seller/orders/{id}/cancel",
                // 购物车与收藏
                "/api/buyer/cart",
                "/api/buyer/cart/{goodsId}",
                "/api/buyer/cart/move-to-favorites",
                "/api/buyer/favorites",
                "/api/buyer/favorites/{goodsId}");

        assertThat(body).doesNotContain(
                "/api/seller/goods/{id}/re-list",
                "/api/buyer/intentions",
                "/api/buyer/history",
                "/api/seller/intentions",
                "/api/seller/history");
    }
}
