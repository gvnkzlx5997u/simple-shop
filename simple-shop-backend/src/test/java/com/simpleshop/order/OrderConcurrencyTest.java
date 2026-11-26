package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.data.domain.PageRequest;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.address.entity.Address;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.order.dto.PlaceOrderItemRequest;
import com.simpleshop.order.dto.PlaceOrderRequest;
import com.simpleshop.testing.ConcurrencyTestBase;

/**
 * 订单并发：下单与库存在商品行上串行，订单状态在订单行上串行。
 *
 * <p>成交扣减的并发（两笔订单抢最后一件、同一订单重复确认）随「买家确认收货」一起验证：
 * 那个入口在阶段四才出现，现在没有可并发触发的完成动作。
 */
class OrderConcurrencyTest extends ConcurrencyTestBase {

    @Test
    @DisplayName("下单不预占：库存只有 1 时也能同时存在两笔待支付订单，库存不变")
    void placingDoesNotReserveStock() {
        Goods goods = publishedGoods(1);
        BuyerAccount buyer = createBuyer();
        Address address = createAddress(buyer);
        PlaceOrderRequest request = new PlaceOrderRequest(
                address.getId(),
                List.of(new PlaceOrderItemRequest(goods.getId(), 1)));

        buyerOrders.place(buyer.getId(), request);
        buyerOrders.place(buyer.getId(), request);

        assertThat(stockItems.findById(goods.getId()).orElseThrow().getAvailable()).isEqualTo(1);
        assertThat(orders.search(null, buyer.getId(), null, PageRequest.of(0, 10))
                .getTotalElements())
                .isEqualTo(2);
    }
}
