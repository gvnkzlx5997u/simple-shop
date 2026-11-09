package com.simpleshop.order.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.entity.OrderLine;
import com.simpleshop.testing.DataLayerTestBase;

/**
 * 序列起点来自数据库：验证「当日最大序列」的取数口径与唯一约束能接住生成结果。
 */
class OrderNoGeneratorDataTest extends DataLayerTestBase {

    @Test
    @DisplayName("重启后从库内当日最大序列继续，不重发已用编号")
    void seedsFromMaxSequenceOfTheDay() {
        BuyerAccount buyer = createBuyer();
        Goods goods = createGoods(createCategory());
        Order existing = createOrder(buyer, goods, 1);
        flushAndClear();

        String prefix = existing.getOrderNo().substring(0, 10);
        long used = Long.parseLong(existing.getOrderNo().substring(10));

        OrderNoGenerator restarted = new OrderNoGenerator(orders);
        assertThat(restarted.next())
                .isEqualTo(prefix + String.format(Locale.ROOT, "%06d", used + 1));
    }

    @Test
    @DisplayName("连续取号都能落库，不触发 uk_order_no")
    void generatedNumbersAreInsertable() {
        BuyerAccount buyer = createBuyer();
        Goods goods = createGoods(createCategory());

        Order first = place(buyer, goods, orderNoGenerator.next());
        OrderNoGenerator restarted = new OrderNoGenerator(orders);
        Order second = place(buyer, goods, restarted.next());

        assertThat(second.getOrderNo()).isNotEqualTo(first.getOrderNo());
        assertThat(orders.findByOrderNo(second.getOrderNo())).isPresent();
    }

    private Order place(BuyerAccount buyer, Goods goods, String orderNo) {
        return orders.saveAndFlush(Order.place(orderNo, buyer.getId(),
                List.of(new OrderLine(goods.getId(), goods.getName(), goods.getPrice(), 1))));
    }
}
