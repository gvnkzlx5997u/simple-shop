package com.simpleshop.favorite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.simpleshop.goods.entity.Goods;
import com.simpleshop.testing.ApiTestBase;

/**
 * 收藏数据层：重复收藏只留一行，且收藏时间按 UTC 写库（写入走原生 upsert，没有实体回调兜底）。
 */
class FavoriteDataTest extends ApiTestBase {

    private static final String BUYER_PASSWORD = "Buyer@1234";

    @Test
    @DisplayName("重复收藏在库里只留一行")
    void duplicateAddKeepsSingleRow() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());

        addFavorite(token, goods.getId());
        LocalDateTime first = storedCreateAt(goods.getId());
        addFavorite(token, goods.getId());

        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from simpleshop_favorite where fk_goods_id = ?",
                Integer.class, goods.getId());
        assertThat(rows).isEqualTo(1);
        assertThat(storedCreateAt(goods.getId())).isEqualTo(first);
    }

    @Test
    @DisplayName("收藏时间按 UTC 写库")
    void storesCreateAtInUtc() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());

        addFavorite(token, goods.getId());

        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        assertThat(storedCreateAt(goods.getId()))
                .isBetween(nowUtc.minusMinutes(10), nowUtc.plusMinutes(10));
    }

    private String buyerToken() throws Exception {
        String username = unique("fd-buyer-");
        assertApiCode(registerBuyer(username, BUYER_PASSWORD, "13800000000", "默认地点"), 0);
        return loginBuyer(username, BUYER_PASSWORD);
    }

    private void addFavorite(String token, String goodsId) throws Exception {
        assertApiCode(
                mockMvc.perform(post("/api/buyer/favorites")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("goods_id", goodsId))))
                        .andReturn(),
                0);
    }

    private LocalDateTime storedCreateAt(String goodsId) {
        return jdbcTemplate.queryForObject(
                "select create_at from simpleshop_favorite where fk_goods_id = ?",
                LocalDateTime.class, goodsId);
    }
}
