package com.simpleshop.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.address.entity.Address;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.enums.OffShelfReason;
import com.simpleshop.order.dto.PayOrderRequest;
import com.simpleshop.order.dto.PlaceOrderItemRequest;
import com.simpleshop.order.dto.PlaceOrderRequest;
import com.simpleshop.order.entity.Order;
import com.simpleshop.order.enums.OrderStatus;
import com.simpleshop.testing.ConcurrencyTestBase;

/**
 * 订单并发：状态迁移在订单行上串行，库存在库存行上串行。
 *
 * <p>四个场景对应四种「两个人都以为能改」：重复点击支付、过期订单支付与超时取消撞车、
 * 两笔订单抢最后一件、买家确认收货与卖家取消同时发生。断言只落在最终状态与账上，
 * 不断言谁先谁后——顺序本身不该被测试固定。
 */
class OrderConcurrencyTest extends ConcurrencyTestBase {

    private static final String OK = "OK";

    @Test
    @DisplayName("并发重复支付：只成功一次，支付时间与渠道只落一次")
    void payingTwiceSucceedsOnce() throws Exception {
        Goods goods = publishedGoods(3);
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer, goods, 1);

        List<String> outcomes = runConcurrently(2, index ->
                () -> payOutcome(buyer.getId(), order.getId()));

        assertThat(outcomes).containsExactlyInAnyOrder(OK, "PAYMENT_NOT_ALLOWED");
        Order paid = orders.findById(order.getId()).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.getPaidAt()).isNotNull();
        assertThat(paymentRefunds.findByOrderIdOrderByRefundedAtDesc(order.getId())).isEmpty();
    }

    @Test
    @DisplayName("过期订单：支付被拒、超时取消成功，不产生退款流水")
    void expiredOrderCannotBePaidWhileTimeoutCancels() throws Exception {
        Goods goods = publishedGoods(3);
        BuyerAccount buyer = createBuyer();
        Order order = placeOrder(buyer, goods, 1);
        LocalDateTime expired = LocalDateTime.now(ZoneOffset.UTC).minusHours(25);
        jdbcTemplate.update(
                "update simpleshop_order set create_at = ?, update_at = ? where id = ?",
                expired, expired, order.getId());

        List<String> outcomes = runConcurrently(2, index -> index == 0
                ? () -> payOutcome(buyer.getId(), order.getId())
                : () -> timeouts.cancelExpired(order.getId()) ? OK : "SKIPPED");

        assertThat(outcomes).contains(OK, "PAYMENT_NOT_ALLOWED");
        Order canceled = orders.findById(order.getId()).orElseThrow();
        assertThat(canceled.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(canceled.getPaidAt()).isNull();
        assertThat(paymentRefunds.findByOrderIdOrderByRefundedAtDesc(order.getId())).isEmpty();
    }

    @Test
    @DisplayName("两笔订单同时确认收货：库存扣两次、恰好扣完，不重复扣也不漏扣")
    void concurrentReceiptsDeductExactlyOnce() throws Exception {
        // 库存 2、两笔各 1 件：两笔都应成交，扣减合计 2。
        // 不用「库存 1 + 两笔」来测抢最后一件——第一笔扣到 0 会触发自动下架，
        // 第二笔按「已下架商品成交不扣库存」直接完成，结果取决于谁先拿到锁，断言会变成抛硬币。
        Goods goods = publishedGoods(2);
        BuyerAccount buyer = createBuyer();
        Order first = shippedOrder(buyer, goods, 1);
        Order second = shippedOrder(buyer, goods, 1);

        List<String> outcomes = runConcurrently(2, index ->
                () -> receiptOutcome(buyer.getId(), index == 0 ? first.getId() : second.getId()));

        assertThat(outcomes).containsExactly(OK, OK);
        assertThat(stockItems.findById(goods.getId()).orElseThrow().getAvailable()).isZero();
        assertThat(List.of(statusOf(first), statusOf(second)))
                .containsExactly(OrderStatus.COMPLETED, OrderStatus.COMPLETED);

        Goods soldOut = goodsRepository.findById(goods.getId()).orElseThrow();
        assertThat(soldOut.getStatus()).isEqualTo(GoodsStatus.OFF_SHELF);
        assertThat(soldOut.getOffShelfReason()).isEqualTo(OffShelfReason.SOLD_OUT);
    }

    @Test
    @DisplayName("确认收货 vs 卖家取消：先到先得，只有取消才退款")
    void receiptAndSellerCancelAreSerialized() throws Exception {
        Goods goods = publishedGoods(5);
        BuyerAccount buyer = createBuyer();
        Order order = shippedOrder(buyer, goods, 2);

        List<String> outcomes = runConcurrently(2, index -> index == 0
                ? () -> receiptOutcome(buyer.getId(), order.getId())
                : () -> cancelOutcome(order.getId()));

        assertThat(outcomes).contains(OK);
        Order finalState = orders.findById(order.getId()).orElseThrow();
        assertThat(finalState.getStatus()).isIn(OrderStatus.COMPLETED, OrderStatus.CANCELED);

        boolean refunded = !paymentRefunds
                .findByOrderIdOrderByRefundedAtDesc(order.getId()).isEmpty();
        int stock = stockItems.findById(goods.getId()).orElseThrow().getAvailable();
        if (finalState.getStatus() == OrderStatus.CANCELED) {
            assertThat(refunded).isTrue();
            assertThat(stock).isEqualTo(5);
        } else {
            assertThat(refunded).isFalse();
            assertThat(stock).isEqualTo(3);
        }
    }

    private Order placeOrder(BuyerAccount buyer, Goods goods, int quantity) {
        Address address = createAddress(buyer);
        String orderId = buyerOrders.place(buyer.getId(), new PlaceOrderRequest(
                        address.getId(),
                        List.of(new PlaceOrderItemRequest(goods.getId(), quantity))))
                .orderId();
        return orders.findById(orderId).orElseThrow();
    }

    private String payOutcome(String buyerId, String orderId) {
        try {
            buyerOrders.pay(buyerId, orderId, new PayOrderRequest("ALIPAY"));
            return OK;
        } catch (BusinessException exception) {
            return exception.getErrorCode().name();
        }
    }

    private String receiptOutcome(String buyerId, String orderId) {
        try {
            buyerOrders.confirmReceipt(buyerId, orderId);
            return OK;
        } catch (BusinessException exception) {
            return exception.getErrorCode().name();
        }
    }

    private String cancelOutcome(String orderId) {
        try {
            sellerOrders.cancel(orderId);
            return OK;
        } catch (BusinessException exception) {
            return exception.getErrorCode().name();
        }
    }

    private OrderStatus statusOf(Order order) {
        return orders.findById(order.getId()).orElseThrow().getStatus();
    }
}
