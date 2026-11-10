package com.simpleshop.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.entity.GoodsImage;
import com.simpleshop.goods.enums.OffShelfReason;
import com.simpleshop.testing.ApiTestBase;

/**
 * 购物车接口：幂等、失效派生、鉴权与买家隔离。
 */
class CartApiTest extends ApiTestBase {

    private static final String BUYER_PASSWORD = "Buyer@1234";
    private static final String SELLER_PASSWORD = "Abcd@1234";

    @Test
    @DisplayName("游客与卖家令牌都不能访问购物车")
    void rejectsGuestAndSeller() throws Exception {
        CategoryL2 category = createCategory();
        Goods goods = createGoods(category);

        assertApiStatusAndCode(
                mockMvc.perform(post("/api/buyer/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("goods_id", goods.getId())))).andReturn(),
                401, 10002);
        assertApiStatusAndCode(
                mockMvc.perform(get("/api/buyer/cart")).andReturn(), 401, 10002);

        String sellerToken = loginSeller(SELLER_PASSWORD);
        assertApiStatusAndCode(
                mockMvc.perform(get("/api/buyer/cart").header("Authorization", bearer(sellerToken)))
                        .andReturn(),
                401, 10002);
    }

    @Test
    @DisplayName("加购在售商品后列表返回完整展示字段")
    void addsOnSaleGoods() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());

        assertApiCode(addToCart(token, goods.getId()), 0);

        JsonNode items = cartItems(token);
        assertThat(items).hasSize(1);
        JsonNode item = items.get(0);
        assertThat(item.path("goods_id").asText()).isEqualTo(goods.getId());
        assertThat(item.path("name").asText()).isEqualTo(goods.getName());
        assertThat(item.path("price").decimalValue()).isEqualByComparingTo("99.99");
        assertThat(item.path("stock").asInt()).isEqualTo(3);
        assertThat(item.path("status").asText()).isEqualTo("ON_SALE");
        assertThat(item.path("off_shelf_reason").isNull()).isTrue();
        assertThat(item.path("main_image_url").isNull()).isTrue();
        assertThat(item.path("selectable").asBoolean()).isTrue();
        assertThat(item.path("added_at").asText()).endsWith("Z");
    }

    @Test
    @DisplayName("在售但库存为 0：仍可加购，列表中仍可选中")
    void onSaleButOutOfStockIsStillSelectable() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());
        setStock(goods, 0);
        flushAndClear();

        // 加购不校验库存：可选与否只看在售状态，库存由前端在下单前另行提示。
        assertApiCode(addToCart(token, goods.getId()), 0);

        JsonNode item = cartItems(token).get(0);
        assertThat(item.path("stock").asInt()).isZero();
        assertThat(item.path("status").asText()).isEqualTo("ON_SALE");
        assertThat(item.path("selectable").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("重复加购只有一行，不新增也不改加购时间")
    void repeatedAddIsIdempotent() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());

        assertApiCode(addToCart(token, goods.getId()), 0);
        String firstAddedAt = cartItems(token).get(0).path("added_at").asText();
        assertApiCode(addToCart(token, goods.getId()), 0);

        JsonNode items = cartItems(token);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).path("added_at").asText()).isEqualTo(firstAddedAt);
    }

    @Test
    @DisplayName("加购已下架商品返回 70001，加购不存在的商品返回 404")
    void rejectsOffShelfAndMissingGoods() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());
        offShelf(goods, OffShelfReason.MANUAL);

        assertApiCode(addToCart(token, goods.getId()), 70001);
        assertApiStatusAndCode(addToCart(token, "G-not-exist"), 404, 20011);
        assertApiFieldError(
                mockMvc.perform(post("/api/buyer/cart")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("goods_id", "  "))))
                        .andReturn(),
                "goods_id");
    }

    @Test
    @DisplayName("空购物车返回空数组")
    void emptyCartReturnsEmptyItems() throws Exception {
        assertThat(cartItems(buyerToken())).isEmpty();
    }

    @Test
    @DisplayName("商品下架后行保留但不可选中，复售后自动恢复可选")
    void offShelfRowStaysAndSelectableFollowsStatus() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());
        assertApiCode(addToCart(token, goods.getId()), 0);

        offShelf(goods, OffShelfReason.STOCK_ZERO);
        flushAndClear();

        JsonNode item = cartItems(token).get(0);
        assertThat(item.path("goods_id").asText()).isEqualTo(goods.getId());
        assertThat(item.path("status").asText()).isEqualTo("OFF_SHELF");
        assertThat(item.path("off_shelf_reason").asText()).isEqualTo("STOCK_ZERO");
        assertThat(item.path("selectable").asBoolean()).isFalse();

        goods.backOnSale();
        goodsRepository.saveAndFlush(goods);
        flushAndClear();

        assertThat(cartItems(token).get(0).path("selectable").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("购物车按加购时间倒序，最新加购的在前")
    void listsNewestFirst() throws Exception {
        String token = buyerToken();
        CategoryL2 category = createCategory();
        Goods first = createGoods(category);
        Goods second = createGoods(category);

        assertApiCode(addToCart(token, first.getId()), 0);
        assertApiCode(addToCart(token, second.getId()), 0);
        // 同一秒内两次插入的 create_at 相同，排序会退化到随机 id 上；把先加购的显式推到两分钟前，
        // 才能稳定测出「按加购时间倒序」而不是碰运气。
        jdbcTemplate.update(
                "update simpleshop_cart_item set create_at = ? where fk_goods_id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2).truncatedTo(ChronoUnit.SECONDS),
                first.getId());

        JsonNode items = cartItems(token);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("goods_id").asText()).isEqualTo(second.getId());
        assertThat(items.get(1).path("goods_id").asText()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("移除后行消失，重复移除仍返回成功")
    void removeIsIdempotent() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());
        assertApiCode(addToCart(token, goods.getId()), 0);

        assertApiCode(removeFromCart(token, goods.getId()), 0);
        assertThat(cartItems(token)).isEmpty();
        assertApiCode(removeFromCart(token, goods.getId()), 0);
    }

    @Test
    @DisplayName("买家之间互相看不到对方的购物车")
    void cartIsIsolatedPerBuyer() throws Exception {
        String first = buyerToken();
        String second = buyerToken();
        Goods goods = createGoods(createCategory());

        assertApiCode(addToCart(first, goods.getId()), 0);

        assertThat(cartItems(first)).hasSize(1);
        assertThat(cartItems(second)).isEmpty();
    }

    @Test
    @DisplayName("列表返回主图与当前库存，没有主图时为 null")
    void showsMainImageAndCurrentStock() throws Exception {
        String token = buyerToken();
        Goods withImage = createGoods(createCategory());
        goodsImages.saveAndFlush(GoodsImage.add(withImage, "/media/goods/main.jpg", true, 1));
        goodsImages.saveAndFlush(GoodsImage.add(withImage, "/media/goods/gallery.jpg", false, 2));
        Goods withoutImage = createGoods(createCategory());
        setStock(withImage, 7);

        assertApiCode(addToCart(token, withImage.getId()), 0);
        assertApiCode(addToCart(token, withoutImage.getId()), 0);

        Map<String, JsonNode> byGoods = new HashMap<>();
        for (JsonNode item : cartItems(token)) {
            byGoods.put(item.path("goods_id").asText(), item);
        }
        assertThat(byGoods.get(withImage.getId()).path("main_image_url").asText())
                .isEqualTo("/media/goods/main.jpg");
        assertThat(byGoods.get(withImage.getId()).path("stock").asInt()).isEqualTo(7);
        assertThat(byGoods.get(withoutImage.getId()).path("main_image_url").isNull()).isTrue();
    }

    @Test
    @DisplayName("其他买家移除不了我的购物车行")
    void otherBuyerCannotRemoveMyRow() throws Exception {
        String mine = buyerToken();
        String other = buyerToken();
        Goods goods = createGoods(createCategory());
        assertApiCode(addToCart(mine, goods.getId()), 0);

        assertApiCode(removeFromCart(other, goods.getId()), 0);

        assertThat(cartItems(mine)).hasSize(1);
    }

    private String buyerToken() throws Exception {
        String username = unique("cart-buyer-");
        assertApiCode(registerBuyer(username, BUYER_PASSWORD, "13800000000", "默认地点"), 0);
        return loginBuyer(username, BUYER_PASSWORD);
    }

    private MvcResult addToCart(String token, String goodsId) throws Exception {
        return mockMvc.perform(post("/api/buyer/cart")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("goods_id", goodsId))))
                .andReturn();
    }

    private MvcResult removeFromCart(String token, String goodsId) throws Exception {
        return mockMvc.perform(delete("/api/buyer/cart/" + goodsId)
                        .header("Authorization", bearer(token)))
                .andReturn();
    }

    private JsonNode cartItems(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/buyer/cart")
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(result, 0);
        return dataOf(result).path("items");
    }
}
