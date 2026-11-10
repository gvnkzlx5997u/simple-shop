package com.simpleshop.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.OffShelfReason;
import com.simpleshop.testing.ApiTestBase;

/**
 * 转收藏：整批成功或整批拒绝，重复转幂等。
 */
class CartToFavoriteApiTest extends ApiTestBase {

    private static final String BUYER_PASSWORD = "Buyer@1234";

    @Test
    @DisplayName("购物车两件转收藏：购物车清空、收藏两件")
    void movesAllSelectedGoods() throws Exception {
        String token = buyerToken();
        CategoryL2 category = createCategory();
        Goods first = createGoods(category);
        Goods second = createGoods(category);
        addToCart(token, first.getId());
        addToCart(token, second.getId());

        MvcResult result = move(token, List.of(first.getId(), second.getId()));
        assertApiCode(result, 0);
        assertThat(dataOf(result).path("moved_count").asInt()).isEqualTo(2);

        assertThat(cartItems(token)).isEmpty();
        assertThat(favoriteItems(token)).hasSize(2);
    }

    @Test
    @DisplayName("重复转同一批：收藏不重复，移动数为 0")
    void repeatedMoveIsIdempotent() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());
        addToCart(token, goods.getId());

        assertApiCode(move(token, List.of(goods.getId())), 0);

        MvcResult again = move(token, List.of(goods.getId()));
        assertApiCode(again, 0);
        assertThat(dataOf(again).path("moved_count").asInt()).isZero();
        assertThat(favoriteItems(token)).hasSize(1);
    }

    @Test
    @DisplayName("入参含已下架商品：整批拒绝，购物车与收藏都不变")
    void rejectsWholeBatchWhenAnyGoodsIsOffShelf() throws Exception {
        String token = buyerToken();
        CategoryL2 category = createCategory();
        Goods onSale = createGoods(category);
        Goods offShelfGoods = createGoods(category);
        addToCart(token, onSale.getId());
        addToCart(token, offShelfGoods.getId());
        offShelf(offShelfGoods, OffShelfReason.MANUAL);
        flushAndClear();

        MvcResult result = move(token, List.of(onSale.getId(), offShelfGoods.getId()));
        assertApiCode(result, 70003);
        // 计划要求「拒绝并指出商品」：被拒的商品 ID 要出现在响应里。
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("message").asText())
                .contains(offShelfGoods.getId());

        assertThat(cartItems(token)).hasSize(2);
        assertThat(favoriteItems(token)).isEmpty();
    }

    @Test
    @DisplayName("空数组被参数校验拒绝")
    void rejectsEmptySelection() throws Exception {
        String token = buyerToken();
        MvcResult result = mockMvc.perform(post("/api/buyer/cart/move-to-favorites")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("goods_ids", List.of()))))
                .andReturn();
        assertApiFieldError(result, "goods_ids");
    }

    @Test
    @DisplayName("入参含不在购物车的商品：忽略该件，只转车里真实存在的行")
    void ignoresGoodsNotInCart() throws Exception {
        String token = buyerToken();
        CategoryL2 category = createCategory();
        Goods inCart = createGoods(category);
        Goods notInCart = createGoods(category);
        addToCart(token, inCart.getId());

        MvcResult result = move(token, List.of(inCart.getId(), notInCart.getId()));
        assertApiCode(result, 0);
        assertThat(dataOf(result).path("moved_count").asInt()).isEqualTo(1);

        assertThat(cartItems(token)).isEmpty();
        JsonNode favorites = favoriteItems(token);
        assertThat(favorites).hasSize(1);
        assertThat(favorites.get(0).path("goods_id").asText()).isEqualTo(inCart.getId());
    }

    @Test
    @DisplayName("未登录不能转收藏")
    void rejectsGuest() throws Exception {
        Goods goods = createGoods(createCategory());
        assertApiStatusAndCode(
                mockMvc.perform(post("/api/buyer/cart/move-to-favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("goods_ids", List.of(goods.getId()))))).andReturn(),
                401, 10002);
    }

    private String buyerToken() throws Exception {
        String username = unique("move-buyer-");
        assertApiCode(registerBuyer(username, BUYER_PASSWORD, "13800000000", "默认地点"), 0);
        return loginBuyer(username, BUYER_PASSWORD);
    }

    private void addToCart(String token, String goodsId) throws Exception {
        assertApiCode(
                mockMvc.perform(post("/api/buyer/cart")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("goods_id", goodsId))))
                        .andReturn(),
                0);
    }

    private MvcResult move(String token, List<String> goodsIds) throws Exception {
        return mockMvc.perform(post("/api/buyer/cart/move-to-favorites")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("goods_ids", goodsIds))))
                .andReturn();
    }

    private JsonNode cartItems(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/buyer/cart")
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(result, 0);
        return dataOf(result).path("items");
    }

    private JsonNode favoriteItems(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/buyer/favorites")
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(result, 0);
        return dataOf(result).path("items");
    }
}
