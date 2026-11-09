package com.simpleshop.goods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.enums.OffShelfReason;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.testing.ApiTestBase;

/**
 * 未完成订单阻断：订单还没走完时，商品不能手动下架、也不能手动把库存改成 0。
 */
class UnfinishedOrderBlockTest extends ApiTestBase {

    private static final String SELLER_PASSWORD = "Abcd@1234";
    private static final int GOODS_HAS_UNFINISHED_ORDER = 20020;

    @Test
    @DisplayName("有未完成订单（已发货）时手动下架被拒，商品保持原状态")
    void blocksManualOffShelf() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        Goods goods = createGoods(createCategory());
        Order order = createOrder(createBuyer(), goods, 1);
        markStatus(order, OrderStatus.SHIPPED);

        MvcResult result = mockMvc.perform(post("/api/seller/goods/" + goods.getId() + "/off-shelf")
                        .header("Authorization", bearer(token)))
                .andReturn();

        assertApiStatusAndCode(result, 200, GOODS_HAS_UNFINISHED_ORDER);
        assertThat(goodsRow(goods).get("status")).isEqualTo(GoodsStatus.ON_SALE.name());
        assertThat(goodsRow(goods).get("off_shelf_reason")).isNull();
    }

    @Test
    @DisplayName("有未完成订单（待确认）时把库存改成 0 被拒，库存与状态都不变")
    void blocksManualStockZero() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        Goods goods = createGoods(createCategory());
        createOrder(createBuyer(), goods, 1);

        MvcResult result = mockMvc.perform(put("/api/seller/goods/" + goods.getId() + "/stock")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("stock", 0))))
                .andReturn();

        assertApiStatusAndCode(result, 200, GOODS_HAS_UNFINISHED_ORDER);
        assertThat(stockOf(goods)).isEqualTo(3);
        assertThat(goodsRow(goods).get("status")).isEqualTo(GoodsStatus.ON_SALE.name());
        assertThat(goodsRow(goods).get("off_shelf_reason")).isNull();
    }

    @Test
    @DisplayName("有未完成订单时改成其他正数不拦")
    void allowsStockIncreaseWithUnfinishedOrder() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        Goods goods = createGoods(createCategory());
        createOrder(createBuyer(), goods, 1);

        MvcResult result = mockMvc.perform(put("/api/seller/goods/" + goods.getId() + "/stock")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("stock", 8))))
                .andReturn();

        assertApiCode(result, 0);
        assertThat(stockOf(goods)).isEqualTo(8);
        assertThat(goodsRow(goods).get("status")).isEqualTo(GoodsStatus.ON_SALE.name());
    }

    @Test
    @DisplayName("订单取消后不再阻断手动下架")
    void unblocksAfterCancellation() throws Exception {
        BuyerAccount buyer = createBuyer();
        String token = loginSeller(SELLER_PASSWORD);
        Goods goods = createGoods(createCategory());
        Order order = createOrder(buyer, goods, 1);
        markStatus(order, OrderStatus.CANCELED);

        MvcResult result = mockMvc.perform(post("/api/seller/goods/" + goods.getId() + "/off-shelf")
                        .header("Authorization", bearer(token)))
                .andReturn();

        assertApiCode(result, 0);
        assertThat(goodsRow(goods).get("status")).isEqualTo(GoodsStatus.OFF_SHELF.name());
        assertThat(goodsRow(goods).get("off_shelf_reason"))
                .isEqualTo(OffShelfReason.MANUAL.name());
    }

    @Test
    @DisplayName("订单交易完成后不再阻断手动改库存为 0")
    void unblocksAfterCompletion() throws Exception {
        BuyerAccount buyer = createBuyer();
        String token = loginSeller(SELLER_PASSWORD);
        Goods goods = createGoods(createCategory());
        Order order = createOrder(buyer, goods, 1);
        markStatus(order, OrderStatus.COMPLETED);

        MvcResult result = mockMvc.perform(put("/api/seller/goods/" + goods.getId() + "/stock")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("stock", 0))))
                .andReturn();

        assertApiCode(result, 0);
        assertThat(stockOf(goods)).isZero();
        assertThat(goodsRow(goods).get("status")).isEqualTo(GoodsStatus.OFF_SHELF.name());
        assertThat(goodsRow(goods).get("off_shelf_reason"))
                .isEqualTo(OffShelfReason.STOCK_ZERO.name());
    }

    /** 直接改库准备订单状态夹具，改完清上下文，避免服务层读到旧实体。 */
    private void markStatus(Order order, OrderStatus status) {
        jdbcTemplate.update("update simpleshop_order set status = ? where id = ?",
                status.name(), order.getId());
        flushAndClear();
    }

    private int stockOf(Goods goods) {
        return stockItems.findById(goods.getId()).orElseThrow().getAvailable();
    }

    private Map<String, Object> goodsRow(Goods goods) {
        return jdbcTemplate.queryForMap(
                "select status, off_shelf_reason from simpleshop_goods where id = ?",
                goods.getId());
    }
}
