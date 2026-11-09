package com.simpleshop.order.service;

import org.springframework.stereotype.Component;

import com.simpleshop.goods.api.UnfinishedOrderPort;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.order.repository.OrderRepository;

/**
 * 未完成订单端口实现：按「商品 + 订单状态」统计订单项。
 *
 * <p>下架路径只读订单表，不加订单行锁：下单会锁商品行，卖家下架也会先锁商品行，
 * 两者在商品行上串行，因此这里读到的是「拿到商品锁之后」的订单状态。
 */
@Component
public class UnfinishedOrderAdapter implements UnfinishedOrderPort {

    private final OrderRepository orders;

    public UnfinishedOrderAdapter(OrderRepository orders) {
        this.orders = orders;
    }

    @Override
    public boolean hasUnfinishedOrder(String goodsId) {
        String id = goodsId == null ? "" : goodsId.trim();
        if (id.isEmpty()) {
            return false;
        }
        return orders.countByGoodsIdAndOrderStatusIn(id, OrderStatus.unfinished()) > 0;
    }
}
