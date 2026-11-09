package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.entity.OrderItem;
import com.simpleshop.order.entity.OrderLine;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.testing.DataLayerTestBase;

class OrderDataTest extends DataLayerTestBase {

    @Test
    @DisplayName("订单与明细一起持久化，明细只存下单快照")
    void persistsOrderWithItemSnapshots() {
        BuyerAccount buyer = createBuyer();
        CategoryL2 category = createCategory();
        Goods first = createGoods(category);
        Goods second = createGoods(category);

        Order order = orders.saveAndFlush(Order.place(
                orderNoGenerator.next(),
                buyer.getId(),
                List.of(
                        new OrderLine(first.getId(), first.getName(), first.getPrice(), 2),
                        new OrderLine(second.getId(), second.getName(), second.getPrice(), 1))));
        flushAndClear();

        Order loaded = orders.findById(order.getId()).orElseThrow();
        List<OrderItem> loadedItems =
                orderItems.findByOrder_IdOrderByGoodsNameAscIdAsc(order.getId());
        assertThat(loaded.getOrderNo()).startsWith("SO");
        assertThat(loaded.getBuyerId()).isEqualTo(buyer.getId());
        assertThat(loaded.getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRM);
        assertThat(loaded.getTotalAmount()).isEqualByComparingTo(new BigDecimal("299.97"));
        assertThat(loaded.getCreateAt()).isNotNull();
        assertThat(loaded.getUpdateAt()).isNotNull();

        assertThat(loadedItems).hasSize(2);
        assertThat(loadedItems).extracting(OrderItem::getGoodsId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
        OrderItem item = loadedItems.stream()
                .filter(row -> row.getGoodsId().equals(first.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(item.getGoodsName()).isEqualTo(first.getName());
        assertThat(item.getUnitPrice()).isEqualByComparingTo(first.getPrice());
        assertThat(item.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("总额按「单价 × 数量」求和，保留两位小数")
    void totalAmountIsSumOfLineAmounts() {
        BuyerAccount buyer = createBuyer();
        CategoryL2 category = createCategory();
        Goods first = createGoods(category);
        Goods second = createGoods(category);

        Order order = orders.saveAndFlush(Order.place(
                orderNoGenerator.next(),
                buyer.getId(),
                List.of(
                        new OrderLine(first.getId(), first.getName(), new BigDecimal("0.01"), 3),
                        new OrderLine(second.getId(), second.getName(), new BigDecimal("12.30"), 7))));

        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("86.13"));
        assertThat(order.getTotalAmount().scale()).isEqualTo(2);

        flushAndClear();
        assertThat(orders.findById(order.getId()).orElseThrow().getTotalAmount())
                .isEqualByComparingTo(new BigDecimal("86.13"));
    }

    @Test
    @DisplayName("构造订单时拒绝空明细、重复商品、非正数量与超精度单价")
    void rejectsInvalidLinesBeforePersisting() {
        BuyerAccount buyer = createBuyer();
        CategoryL2 category = createCategory();
        Goods goods = createGoods(category);
        String orderNo = orderNoGenerator.next();

        assertThatThrownBy(() -> Order.place(orderNo, buyer.getId(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one line");

        assertThatThrownBy(() -> Order.place(orderNo, buyer.getId(), List.of(
                new OrderLine(goods.getId(), goods.getName(), goods.getPrice(), 1),
                new OrderLine(goods.getId(), goods.getName(), goods.getPrice(), 2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate goods");

        assertThatThrownBy(() -> Order.place(orderNo, buyer.getId(), List.of(
                new OrderLine(goods.getId(), goods.getName(), goods.getPrice(), 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");

        assertThatThrownBy(() -> Order.place(orderNo, buyer.getId(), List.of(
                new OrderLine(goods.getId(), goods.getName(), new BigDecimal("9.999"), 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 decimals");

        assertThatThrownBy(() -> Order.place(orderNo, buyer.getId(), List.of(
                new OrderLine(goods.getId(), goods.getName(), BigDecimal.ZERO, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unit price");

        assertThatThrownBy(() -> Order.place("SO" + "9".repeat(40), buyer.getId(), List.of(
                new OrderLine(goods.getId(), goods.getName(), goods.getPrice(), 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("订单号唯一索引拒绝重复编号")
    void rejectsDuplicateOrderNo() {
        BuyerAccount buyer = createBuyer();
        CategoryL2 category = createCategory();
        Goods goods = createGoods(category);
        String orderNo = orderNoGenerator.next();

        orders.saveAndFlush(Order.place(orderNo, buyer.getId(),
                List.of(new OrderLine(goods.getId(), goods.getName(), goods.getPrice(), 1))));

        Order duplicate = Order.place(orderNo, buyer.getId(),
                List.of(new OrderLine(goods.getId(), goods.getName(), goods.getPrice(), 1)));
        assertThatThrownBy(() -> orders.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("买家订单列表只返回自己的订单，按下单时间倒序")
    void pagesBuyerOrdersNewestFirst() {
        BuyerAccount buyer = createBuyer();
        BuyerAccount other = createBuyer();
        Goods goods = createGoods(createCategory());

        Order older = createOrder(buyer, goods, 1);
        Order newer = createOrder(buyer, goods, 1);
        Order foreign = createOrder(other, goods, 1);

        LocalDateTime base = LocalDateTime.now(ZoneOffset.UTC).minusDays(1);
        jdbcTemplate.update("update simpleshop_order set create_at = ?, update_at = ? where id = ?",
                base, base, older.getId());
        jdbcTemplate.update("update simpleshop_order set create_at = ?, update_at = ? where id = ?",
                base.plusHours(1), base.plusHours(1), newer.getId());
        flushAndClear();

        Page<Order> page = orders.search(null, buyer.getId(), null, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Order::getId)
                .containsExactly(newer.getId(), older.getId());

        assertThat(orders.search(null, other.getId(), null, PageRequest.of(0, 10))
                .getContent())
                .extracting(Order::getId)
                .containsExactly(foreign.getId());
    }

    @Test
    @DisplayName("卖家订单列表可按状态筛选，全部状态时返回所有买家的订单")
    void filtersOrdersByStatus() {
        BuyerAccount buyer = createBuyer();
        BuyerAccount other = createBuyer();
        Goods goods = createGoods(createCategory());

        Order pending = createOrder(buyer, goods, 1);
        Order confirmed = createOrder(other, goods, 1);
        jdbcTemplate.update("update simpleshop_order set status = ? where id = ?",
                OrderStatus.CONFIRMED.name(), confirmed.getId());
        flushAndClear();

        Page<Order> all = orders.search(null, null, null, PageRequest.of(0, 10));
        assertThat(all.getContent()).extracting(Order::getId)
                .containsExactlyInAnyOrder(pending.getId(), confirmed.getId());

        Page<Order> confirmedOnly = orders.search(
                OrderStatus.CONFIRMED, null, null, PageRequest.of(0, 10));
        assertThat(confirmedOnly.getContent()).extracting(Order::getId)
                .containsExactly(confirmed.getId());

        assertThat(orders.search(OrderStatus.CONFIRMED, buyer.getId(), null,
                PageRequest.of(0, 10)).getTotalElements())
                .isZero();
    }

    @Test
    @DisplayName("按商品查订单经订单项反查，不包含未购买该商品的订单")
    void findsOrdersByGoods() {
        BuyerAccount buyer = createBuyer();
        CategoryL2 category = createCategory();
        Goods ordered = createGoods(category);
        Goods untouched = createGoods(category);

        Order order = createOrder(buyer, ordered, 2);
        Order otherOrder = createOrder(buyer, untouched, 1);

        assertThat(orders.search(null, null, ordered.getId(), PageRequest.of(0, 10))
                .getContent())
                .extracting(Order::getId)
                .containsExactly(order.getId());

        assertThat(orders.search(null, null, untouched.getId(), PageRequest.of(0, 10))
                .getContent())
                .extracting(Order::getId)
                .containsExactly(otherOrder.getId());
    }

    @Test
    @DisplayName("按订单号与锁定查询都能取到订单")
    void findsOrderByNumberAndWithLock() {
        BuyerAccount buyer = createBuyer();
        Goods goods = createGoods(createCategory());
        Order order = createOrder(buyer, goods, 1);
        flushAndClear();

        assertThat(orders.findByOrderNo(order.getOrderNo())).map(Order::getId).hasValue(order.getId());
        assertThat(orders.findWithLockById(order.getId())).map(Order::isUnfinished).hasValue(true);
        assertThat(orders.findByOrderNo("SO-NOT-EXISTS")).isEmpty();
    }
}
