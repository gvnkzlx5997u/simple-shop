package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simpleshop.order.entity.Order;
import com.simpleshop.order.entity.OrderLine;
import com.simpleshop.order.enums.OrderStatus;

/**
 * 订单状态机：六态的能力矩阵与全部迁移（合法与非法）走实体，不碰数据库。
 */
class OrderStateMachineTest {

    private static final List<Action> ACTIONS = List.of(
            new Action("confirm", Order::canConfirm, Order::confirm, OrderStatus.CONFIRMED),
            new Action("prepare", Order::canPrepare, Order::prepare, OrderStatus.PREPARED),
            new Action("ship", Order::canShip, Order::ship, OrderStatus.SHIPPED),
            new Action("complete", Order::canComplete, Order::complete, OrderStatus.COMPLETED),
            new Action("cancelByBuyer", Order::canCancelByBuyer, Order::cancelByBuyer,
                    OrderStatus.CANCELED),
            new Action("cancelBySeller", Order::canCancelBySeller, Order::cancelBySeller,
                    OrderStatus.CANCELED));

    @Test
    @DisplayName("六态能力矩阵：确认/备货/发货/完成只能按序，取消按买卖双方各自的边界")
    void capabilityMatrixMatchesStateModel() {
        // 顺序：confirm, prepare, ship, complete, cancelByBuyer, cancelBySeller
        assertThat(capabilities(orderIn(OrderStatus.PENDING_CONFIRM)))
                .containsExactly(true, false, false, false, true, true);
        assertThat(capabilities(orderIn(OrderStatus.CONFIRMED)))
                .containsExactly(false, true, false, false, true, true);
        assertThat(capabilities(orderIn(OrderStatus.PREPARED)))
                .containsExactly(false, false, true, false, true, true);
        assertThat(capabilities(orderIn(OrderStatus.SHIPPED)))
                .containsExactly(false, false, false, true, false, true);
        assertThat(capabilities(orderIn(OrderStatus.COMPLETED)))
                .containsExactly(false, false, false, false, false, false);
        assertThat(capabilities(orderIn(OrderStatus.CANCELED)))
                .containsExactly(false, false, false, false, false, false);
    }

    @Test
    @DisplayName("矩阵之外的迁移一律被拒绝：6 状态 × 6 动作，只放行合法的那几条")
    void everyTransitionOutsideTheMatrixThrows() {
        int allowed = 0;
        for (OrderStatus from : OrderStatus.values()) {
            for (Action action : ACTIONS) {
                Order order = orderIn(from);
                if (action.allowed().test(order)) {
                    allowed++;
                    action.perform().accept(order);
                    assertThat(order.getStatus())
                            .as("%s from %s", action.name(), from)
                            .isEqualTo(action.target());
                } else {
                    assertThatThrownBy(() -> action.perform().accept(order))
                            .as("%s from %s", action.name(), from)
                            .isInstanceOf(IllegalStateException.class);
                }
            }
        }
        // 1+1+1+1（推进）+ 3（买家取消）+ 4（卖家取消）= 11 条合法迁移
        assertThat(allowed).isEqualTo(11);
    }

    private static List<Boolean> capabilities(Order order) {
        return List.of(
                order.canConfirm(),
                order.canPrepare(),
                order.canShip(),
                order.canComplete(),
                order.canCancelByBuyer(),
                order.canCancelBySeller());
    }

    /** 用合法迁移把订单推到指定状态；终态用对应的取消/完成动作到达。 */
    private static Order orderIn(OrderStatus status) {
        Order order = Order.place("SO-TEST", "BUYER-1",
                List.of(new OrderLine("G1", "测试商品", new BigDecimal("1.00"), 1)));
        switch (status) {
            case PENDING_CONFIRM -> {
            }
            case CONFIRMED -> order.confirm();
            case PREPARED -> {
                order.confirm();
                order.prepare();
            }
            case SHIPPED -> {
                order.confirm();
                order.prepare();
                order.ship();
            }
            case COMPLETED -> {
                order.confirm();
                order.prepare();
                order.ship();
                order.complete();
            }
            case CANCELED -> order.cancelBySeller();
        }
        return order;
    }

    private record Action(
            String name,
            Predicate<Order> allowed,
            Consumer<Order> perform,
            OrderStatus target) {
    }
}
