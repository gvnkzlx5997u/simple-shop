package com.simpleshop.goods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.enums.OffShelfReason;
import com.simpleshop.testing.ApiTestBase;

/**
 * 下架与库存不再受订单限制：阶段四删掉了「有未完成订单禁止下架 / 改 0」。
 *
 * <p>历史原因：那条限制原本是为了防止「订单没走完、卖家却把商品下架」。现在库存不预占、
 * 成交才扣减，且已下架商品成交不扣库存，限制就没有意义了。
 */
class GoodsOffShelfWithoutBlockTest extends ApiTestBase {

    @Test
    @DisplayName("有未完成订单时仍可手动下架，商品进入手动下架终态")
    void offShelfAllowedWithUnfinishedOrder() throws Exception {
        BuyerAccount buyer = createBuyer();
        CategoryL2 category = createCategory();
        Goods goods = createGoods(category);
        createOrder(buyer, goods, 1);

        mockMvc.perform(post("/api/seller/goods/{id}/off-shelf", goods.getId())
                        .header("Authorization", bearer(loginSeller("Abcd@1234"))))
                .andExpect(status().isOk());

        Goods after = reloadGoods(goods);
        assertThat(after.getStatus()).isEqualTo(GoodsStatus.OFF_SHELF);
        assertThat(after.getOffShelfReason()).isEqualTo(OffShelfReason.MANUAL);
    }

    @Test
    @DisplayName("有未完成订单时仍可把库存改成 0，触发库存耗尽下架")
    void zeroStockAllowedWithUnfinishedOrder() throws Exception {
        BuyerAccount buyer = createBuyer();
        CategoryL2 category = createCategory();
        Goods goods = createGoods(category);
        createOrder(buyer, goods, 1);

        mockMvc.perform(put("/api/seller/goods/{id}/stock", goods.getId())
                        .header("Authorization", bearer(loginSeller("Abcd@1234")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("stock", 0))))
                .andExpect(status().isOk());

        Goods after = reloadGoods(goods);
        assertThat(after.getStatus()).isEqualTo(GoodsStatus.OFF_SHELF);
        assertThat(after.getOffShelfReason()).isEqualTo(OffShelfReason.STOCK_ZERO);
        assertThat(stockItems.findById(goods.getId()).orElseThrow().getAvailable()).isZero();
    }

    private Goods reloadGoods(Goods goods) {
        flushAndClear();
        return goodsRepository.findById(goods.getId()).orElseThrow();
    }
}
