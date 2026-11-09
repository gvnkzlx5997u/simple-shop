package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.data.domain.PageRequest;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.enums.OffShelfReason;
import com.simpleshop.order.dto.PlaceOrderItemRequest;
import com.simpleshop.order.dto.PlaceOrderRequest;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.testing.ConcurrencyTestBase;

/**
 * 订单并发：无预占模型下，成交扣减在库存行上串行；订单状态在订单行上串行。
 */
class OrderConcurrencyTest extends ConcurrencyTestBase {

    private static final String COMPLETED = "COMPLETED";
    private static final String STOCK_SHORTAGE = "ORDER_STOCK_SHORTAGE";
    private static final String STATUS_INVALID = "ORDER_STATUS_INVALID";

    @Test
    @DisplayName("两笔订单抢最后一件：先完成者成功，后者整单缺货拒绝，库存不出现负数")
    void onlyOneOrderCanTakeTheLastItem() throws Exception {
        Goods goods = publishedGoods(1);
        BuyerAccount buyer = createBuyer();
        Order first = shippedOrder(buyer, goods, 1);
        Order second = shippedOrder(buyer, goods, 1);

        List<String> outcomes = runConcurrently(2, index ->
                () -> completeOutcome(index == 0 ? first.getId() : second.getId()));

        assertThat(outcomes).containsExactlyInAnyOrder(COMPLETED, STOCK_SHORTAGE);
        assertThat(stockItems.findById(goods.getId()).orElseThrow().getAvailable()).isZero();
        assertThat(List.of(orderStatusOf(first), orderStatusOf(second)))
                .containsExactlyInAnyOrder(OrderStatus.COMPLETED, OrderStatus.SHIPPED);

        Goods soldOut = reload(goods);
        assertThat(soldOut.getStatus()).isEqualTo(GoodsStatus.OFF_SHELF);
        assertThat(soldOut.getOffShelfReason()).isEqualTo(OffShelfReason.SOLD_OUT);
    }

    @Test
    @DisplayName("同一订单并发完成只扣一次库存，后到的请求拿到状态不允许")
    void completingSameOrderTwiceDeductsOnce() throws Exception {
        Goods goods = publishedGoods(5);
        BuyerAccount buyer = createBuyer();
        Order order = shippedOrder(buyer, goods, 2);

        List<String> outcomes = runConcurrently(2, index -> () -> completeOutcome(order.getId()));

        assertThat(outcomes).containsExactlyInAnyOrder(COMPLETED, STATUS_INVALID);
        assertThat(stockItems.findById(goods.getId()).orElseThrow().getAvailable()).isEqualTo(3);
        assertThat(orderStatusOf(order)).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("下单不预占：库存只有 1 时也能同时存在两笔待确认订单")
    void placingDoesNotReserveStock() {
        Goods goods = publishedGoods(1);
        BuyerAccount buyer = createBuyer();
        PlaceOrderRequest request = new PlaceOrderRequest(
                List.of(new PlaceOrderItemRequest(goods.getId(), 1)));

        buyerOrders.place(buyer.getId(), request);
        buyerOrders.place(buyer.getId(), request);

        assertThat(stockItems.findById(goods.getId()).orElseThrow().getAvailable()).isEqualTo(1);
        assertThat(orders.search(null, buyer.getId(), null, PageRequest.of(0, 10))
                .getTotalElements())
                .isEqualTo(2);
    }

    /** 把业务拒绝翻译成错误码名，便于在两个并发分支之间做集合断言。 */
    private String completeOutcome(String orderId) {
        try {
            sellerOrders.complete(orderId);
            return COMPLETED;
        } catch (BusinessException exception) {
            return exception.getErrorCode().name();
        }
    }
}
