package com.simpleshop.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.testing.ApiTestBase;

/**
 * 购物车数据层：唯一键、外键与库内时间。
 *
 * <p>加购走原生 upsert，绕过了实体生命周期回调，因此「库里存的是 UTC」必须单独验证：
 * 一旦绑定参数按本地时区写库，接口看起来正常，读出来的时间却会整体偏移。
 */
class CartDataTest extends ApiTestBase {

    private static final String BUYER_PASSWORD = "Buyer@1234";

    @Test
    @DisplayName("重复加购在库里只留一行，且不刷新加购时间")
    void duplicateAddKeepsSingleRow() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());

        addToCart(token, goods.getId());
        LocalDateTime first = storedCreateAt(goods.getId());
        addToCart(token, goods.getId());

        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from simpleshop_cart_item where fk_goods_id = ?",
                Integer.class, goods.getId());
        assertThat(rows).isEqualTo(1);
        assertThat(storedCreateAt(goods.getId())).isEqualTo(first);
    }

    @Test
    @DisplayName("加购时间按 UTC 写库")
    void storesCreateAtInUtc() throws Exception {
        String token = buyerToken();
        Goods goods = createGoods(createCategory());

        addToCart(token, goods.getId());

        LocalDateTime stored = storedCreateAt(goods.getId());
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        // 允许几分钟误差，但不允许整体偏移时区（东八区会差 8 小时，直接超出区间）。
        assertThat(stored).isBetween(nowUtc.minusMinutes(10), nowUtc.plusMinutes(10));
    }

    @Test
    @DisplayName("买家不存在时外键拒绝写入")
    void rejectsUnknownBuyerByForeignKey() throws Exception {
        Goods goods = createGoods(createCategory());
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into simpleshop_cart_item (id, fk_buyer_id, fk_goods_id, create_at)"
                        + " values (?, ?, ?, ?)",
                "CI-test", "B-not-exist", goods.getId(),
                LocalDateTime.now(ZoneOffset.UTC)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private String buyerToken() throws Exception {
        String username = unique("cd-buyer-");
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

    private LocalDateTime storedCreateAt(String goodsId) {
        return jdbcTemplate.queryForObject(
                "select create_at from simpleshop_cart_item where fk_goods_id = ?",
                LocalDateTime.class, goodsId);
    }
}
