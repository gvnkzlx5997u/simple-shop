package com.simpleshop.favorite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
 * 收藏接口：幂等、失效派生（不可进详情）、取消不校验状态、买家隔离。
 */
class FavoriteApiTest extends ApiTestBase {

    private static final String BUYER_PASSWORD = "Buyer@1234";

    @Test
    @DisplayName("游客与卖家令牌都不能收藏")
    void rejectsGuestAndSeller() throws Exception {
        Goods goods = createGoods(createCategory());
        assertApiStatusAndCode(
                mockMvc.perform(post("/api/buyer/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("goods_id", goods.getId())))).andReturn(),
                401, 10002);
        assertApiStatusAndCode(
                mockMvc.perform(get("/api/buyer/favorites")).andReturn(), 401, 10002);

        String sellerToken = loginSeller("Abcd@1234");
        assertApiStatusAndCode(
                mockMvc.perform(get("/api/buyer/favorites")
                        .header("Authorization", bearer(sellerToken))).andReturn(),
                401, 10002);
    }

    @Test
    @DisplayName("收藏在售商品后出现在我的收藏")
    void addsFavorite() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());

        assertApiCode(addFavorite(token, goods.getId()), 0);

        JsonNode items = favoriteItems(token);
        assertThat(items).hasSize(1);
        JsonNode item = items.get(0);
        assertThat(item.path("goods_id").asText()).isEqualTo(goods.getId());
        assertThat(item.path("name").asText()).isEqualTo(goods.getName());
        assertThat(item.path("detail_available").asBoolean()).isTrue();
        assertThat(item.path("favorited_at").asText()).endsWith("Z");
    }

    @Test
    @DisplayName("重复收藏只有一行")
    void repeatedAddIsIdempotent() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());

        assertApiCode(addFavorite(token, goods.getId()), 0);
        assertApiCode(addFavorite(token, goods.getId()), 0);

        assertThat(favoriteItems(token)).hasSize(1);
    }

    @Test
    @DisplayName("收藏已下架商品返回 70002")
    void rejectsOffShelfGoods() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());
        offShelf(goods, OffShelfReason.MANUAL);

        assertApiCode(addFavorite(token, goods.getId()), 70002);
        assertThat(favoriteItems(token)).isEmpty();
    }

    @Test
    @DisplayName("已下架收藏保留展示但不可进详情")
    void offShelfFavoriteStaysButHasNoDetail() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());
        assertApiCode(addFavorite(token, goods.getId()), 0);

        offShelf(goods, OffShelfReason.MANUAL);
        flushAndClear();

        JsonNode item = favoriteItems(token).get(0);
        assertThat(item.path("goods_id").asText()).isEqualTo(goods.getId());
        assertThat(item.path("status").asText()).isEqualTo("OFF_SHELF");
        assertThat(item.path("detail_available").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("取消收藏后行消失，重复取消仍成功；已下架也能取消")
    void removeIsIdempotentAndAllowsOffShelf() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());
        assertApiCode(addFavorite(token, goods.getId()), 0);

        offShelf(goods, OffShelfReason.MANUAL);
        flushAndClear();

        assertApiCode(removeFavorite(token, goods.getId()), 0);
        assertThat(favoriteItems(token)).isEmpty();
        assertApiCode(removeFavorite(token, goods.getId()), 0);
    }

    @Test
    @DisplayName("我的收藏按收藏时间倒序")
    void listsNewestFirst() throws Exception {
        String token = buyerToken();
        CategoryL2 category = createCategory();
        Goods first = createGoods(category);
        Goods second = createGoods(category);

        assertApiCode(addFavorite(token, first.getId()), 0);
        assertApiCode(addFavorite(token, second.getId()), 0);
        // 同一秒内两次插入的 create_at 相同，排序会退化到随机 id 上；把先收藏的显式推到两分钟前，
        // 才能稳定测出「按收藏时间倒序」而不是碰运气。
        jdbcTemplate.update(
                "update simpleshop_favorite set create_at = ? where fk_goods_id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2).truncatedTo(ChronoUnit.SECONDS),
                first.getId());

        JsonNode items = favoriteItems(token);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("goods_id").asText()).isEqualTo(second.getId());
        assertThat(items.get(1).path("goods_id").asText()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("收藏按买家隔离")
    void favoritesAreIsolatedPerBuyer() throws Exception {
        String first = buyerToken();
        String second = buyerToken();
        Goods goods = createGoods(createCategory());

        assertApiCode(addFavorite(first, goods.getId()), 0);

        assertThat(favoriteItems(first)).hasSize(1);
        assertThat(favoriteItems(second)).isEmpty();
    }

    @Test
    @DisplayName("商品复售后收藏行自动恢复可进详情，无需同步")
    void restockRestoresDetailAvailable() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());
        assertApiCode(addFavorite(token, goods.getId()), 0);

        offShelf(goods, OffShelfReason.STOCK_ZERO);
        flushAndClear();
        assertThat(favoriteItems(token).get(0).path("detail_available").asBoolean()).isFalse();

        goods.backOnSale();
        goodsRepository.saveAndFlush(goods);
        flushAndClear();
        assertThat(favoriteItems(token).get(0).path("detail_available").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("收藏不存在的商品返回 404")
    void rejectsMissingGoods() throws Exception {
        String token = buyerToken();
        assertApiStatusAndCode(addFavorite(token, "G-not-exist"), 404, 20011);
    }

    @Test
    @DisplayName("重复收藏不改变收藏时间")
    void repeatedAddKeepsFavoritedAt() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());
        assertApiCode(addFavorite(token, goods.getId()), 0);
        String firstFavoritedAt = favoriteItems(token).get(0).path("favorited_at").asText();

        assertApiCode(addFavorite(token, goods.getId()), 0);

        assertThat(favoriteItems(token).get(0).path("favorited_at").asText())
                .isEqualTo(firstFavoritedAt);
    }

    @Test
    @DisplayName("超过上限的 page_size 被参数校验拒绝")
    void rejectsPageSizeOverLimit() throws Exception {
        String token = buyerToken();
        assertApiStatusAndCode(
                mockMvc.perform(get("/api/buyer/favorites")
                        .param("page", "1")
                        .param("page_size", "101")
                        .header("Authorization", bearer(token)))
                        .andReturn(),
                400, 50002);
    }

    @Test
    @DisplayName("详情页的收藏状态：未收藏 false，收藏后 true，取消后回 false")
    void reportsFavoriteStatus() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());

        assertThat(favoriteStatus(token, goods.getId())).isFalse();
        assertApiCode(addFavorite(token, goods.getId()), 0);
        assertThat(favoriteStatus(token, goods.getId())).isTrue();
        assertApiCode(removeFavorite(token, goods.getId()), 0);
        assertThat(favoriteStatus(token, goods.getId())).isFalse();
    }

    @Test
    @DisplayName("收藏状态按买家隔离，商品下架后仍为 true")
    void favoriteStatusIsPerBuyerAndSurvivesOffShelf() throws Exception {
        String mine = buyerToken();
        String other = buyerToken();
        Goods goods = createGoods(createCategory());
        assertApiCode(addFavorite(mine, goods.getId()), 0);

        assertThat(favoriteStatus(mine, goods.getId())).isTrue();
        assertThat(favoriteStatus(other, goods.getId())).isFalse();

        offShelf(goods, OffShelfReason.MANUAL);
        flushAndClear();
        assertThat(favoriteStatus(mine, goods.getId())).isTrue();
    }

    @Test
    @DisplayName("未登录查不了收藏状态")
    void favoriteStatusRequiresLogin() throws Exception {
        Goods goods = createGoods(createCategory());
        assertApiStatusAndCode(
                mockMvc.perform(get("/api/buyer/favorites/" + goods.getId())).andReturn(),
                401, 10002);
    }

    private boolean favoriteStatus(String token, String goodsId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/buyer/favorites/" + goodsId)
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(result, 0);
        return dataOf(result).path("favorited").asBoolean();
    }

    private String buyerToken() throws Exception {
        String username = unique("fav-buyer-");
        assertApiCode(registerBuyer(username, BUYER_PASSWORD, "13800000000", "默认地点"), 0);
        return loginBuyer(username, BUYER_PASSWORD);
    }

    private MvcResult addFavorite(String token, String goodsId) throws Exception {
        return mockMvc.perform(post("/api/buyer/favorites")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("goods_id", goodsId))))
                .andReturn();
    }

    private MvcResult removeFavorite(String token, String goodsId) throws Exception {
        return mockMvc.perform(delete("/api/buyer/favorites/" + goodsId)
                        .header("Authorization", bearer(token)))
                .andReturn();
    }

    private JsonNode favoriteItems(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/buyer/favorites")
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(result, 0);
        return dataOf(result).path("items");
    }
}
