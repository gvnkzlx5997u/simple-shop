package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simpleshop.order.entity.Order;
import com.simpleshop.order.entity.OrderLine;
import com.simpleshop.order.entity.ReceiverSnapshot;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.payment.api.PaymentChannel;

/**
 * 订单状态机：七态的能力矩阵与全部迁移（合法与非法）走实体，不碰数据库。
 *
 * <p>这里只验证「状态能不能走这条边」。谁有权触发（卖家不能确认收货、买家不能确认）
 * 是服务层的权限判断，不写在实体里——实体的方法是能力，不是权限。
 */
class OrderStateMachineTest {

    private static final LocalDateTime PAID_AT =
            LocalDateTime.of(2026, 11, 26, 2, 0, 0);

    private static final List<Action> ACTIONS = List.of(
            new Action("pay", Order::canPay, order -> order.pay(PaymentChannel.ALIPAY, PAID_AT),
                    OrderStatus.PAID),
            new Action("confirm", Order::canConfirm, Order::confirm, OrderStatus.CONFIRMED),
            new Action("prepare", Order::canPrepare, Order::prepare, OrderStatus.PREPARED),
            new Action("ship", Order::canShip, order -> order.ship("顺丰速运", "SF123456", null),
                    OrderStatus.SHIPPED),
            new Action("confirmReceipt", Order::canConfirmReceipt, Order::confirmReceipt,
                    OrderStatus.COMPLETED),
            new Action("cancelByBuyer", Order::canCancelByBuyer, Order::cancelByBuyer,
                    OrderStatus.CANCELED),
            new Action("cancelBySeller", Order::canCancelBySeller, Order::cancelBySeller,
                    OrderStatus.CANCELED));

    @Test
    @DisplayName("七态能力矩阵：支付、确认、备货、发货、收货按序，取消按买卖双方各自的边界")
    void capabilityMatrixMatchesStateModel() {
        // 顺序：pay, confirm, prepare, ship, confirmReceipt, cancelByBuyer, cancelBySeller
        assertThat(capabilities(orderIn(OrderStatus.PENDING_PAYMENT)))
                .containsExactly(true, false, false, false, false, true, true);
        assertThat(capabilities(orderIn(OrderStatus.PAID)))
                .containsExactly(false, true, false, false, false, true, true);
        assertThat(capabilities(orderIn(OrderStatus.CONFIRMED)))
                .containsExactly(false, false, true, false, false, true, true);
        assertThat(capabilities(orderIn(OrderStatus.PREPARED)))
                .containsExactly(false, false, false, true, false, true, true);
        // 已发货之后买家不能再取消，只剩卖家能取消或等待买家确认收货
        assertThat(capabilities(orderIn(OrderStatus.SHIPPED)))
                .containsExactly(false, false, false, false, true, false, true);
        assertThat(capabilities(orderIn(OrderStatus.COMPLETED)))
                .containsExactly(false, false, false, false, false, false, false);
        assertThat(capabilities(orderIn(OrderStatus.CANCELED)))
                .containsExactly(false, false, false, false, false, false, false);
    }

    @Test
    @DisplayName("矩阵之外一律拒绝：7 状态 × 7 动作，只放行 14 条合法迁移")
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
        // 5 条按序推进 + 4 条买家取消 + 5 条卖家取消（多一个已发货）= 14
        assertThat(allowed).isEqualTo(14);
    }

    @Test
    @DisplayName("支付：成功写渠道与时间，重复支付被拒绝")
    void payRecordsChannelAndTimeOnlyOnce() {
        Order order = orderIn(OrderStatus.PENDING_PAYMENT);
        order.pay(PaymentChannel.WECHAT, PAID_AT);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentChannel()).isEqualTo(PaymentChannel.WECHAT);
        assertThat(order.getPaidAt()).isEqualTo(PAID_AT);
        assertThat(order.isPaid()).isTrue();
        assertThatThrownBy(() -> order.pay(PaymentChannel.ALIPAY, PAID_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("发货：承运商与单号必填，发货后可改物流但状态不变")
    void shipRequiresLogisticsAndAllowsCorrection() {
        Order order = orderIn(OrderStatus.PREPARED);
        assertThatThrownBy(() -> order.ship(" ", "SF1", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> order.ship("顺丰速运", "", null))
                .isInstanceOf(IllegalArgumentException.class);

        order.ship("顺丰速运", "SF1\nSF2", "分两个包裹");
        assertThat(order.getTrackingNo()).contains("\n");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);

        order.updateLogistics("京东物流", "JD9", null);
        assertThat(order.getCarrier()).isEqualTo("京东物流");
        assertThat(order.getLogisticsRemark()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    @DisplayName("取消后的订单不再允许修改物流")
    void canceledOrderRejectsLogisticsUpdate() {
        Order order = orderIn(OrderStatus.SHIPPED);
        order.cancelBySeller();

        assertThat(order.canUpdateLogistics()).isFalse();
        assertThatThrownBy(() -> order.updateLogistics("顺丰速运", "SF1", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("未支付的订单没有支付渠道与支付时间")
    void unpaidOrderHasNoPaymentFacts() {
        Order order = orderIn(OrderStatus.PENDING_PAYMENT);

        assertThat(order.getPaymentChannel()).isNull();
        assertThat(order.getPaidAt()).isNull();
        assertThat(order.isPaid()).isFalse();
    }

    private static List<Boolean> capabilities(Order order) {
        return List.of(
                order.canPay(),
                order.canConfirm(),
                order.canPrepare(),
                order.canShip(),
                order.canConfirmReceipt(),
                order.canCancelByBuyer(),
                order.canCancelBySeller());
    }

    /** 用合法迁移把订单推到指定状态；终态用取消或确认收货到达。 */
    private static Order orderIn(OrderStatus status) {
        Order order = Order.place("SO-TEST", "BUYER-1",
                new ReceiverSnapshot("张三", "13800000000", "北京市朝阳区某路 1 号"),
                List.of(new OrderLine("G1", "测试商品", new BigDecimal("1.00"), 1)));
        switch (status) {
            case PENDING_PAYMENT -> {
            }
            case PAID -> order.pay(PaymentChannel.ALIPAY, PAID_AT);
            case CONFIRMED -> {
                order.pay(PaymentChannel.ALIPAY, PAID_AT);
                order.confirm();
            }
            case PREPARED -> {
                order.pay(PaymentChannel.ALIPAY, PAID_AT);
                order.confirm();
                order.prepare();
            }
            case SHIPPED -> {
                order.pay(PaymentChannel.ALIPAY, PAID_AT);
                order.confirm();
                order.prepare();
                order.ship("顺丰速运", "SF123456", null);
            }
            case COMPLETED -> {
                order.pay(PaymentChannel.ALIPAY, PAID_AT);
                order.confirm();
                order.prepare();
                order.ship("顺丰速运", "SF123456", null);
                order.confirmReceipt();
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
