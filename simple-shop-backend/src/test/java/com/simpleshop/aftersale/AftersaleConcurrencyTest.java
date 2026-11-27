package com.simpleshop.aftersale;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.aftersale.dto.AftersaleAppliedData;
import com.simpleshop.aftersale.dto.AftersaleApplyRequest;
import com.simpleshop.aftersale.dto.AftersaleCompleteRequest;
import com.simpleshop.aftersale.enums.AftersaleResult;
import com.simpleshop.aftersale.enums.AftersaleStatus;
import com.simpleshop.aftersale.service.BuyerAftersaleService;
import com.simpleshop.aftersale.service.SellerAftersaleService;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.order.entity.Order;
import com.simpleshop.testing.ConcurrencyTestBase;

/**
 * 售后并发：退款额度靠订单项行锁串行化。
 *
 * <p>两笔售后单针对同一订单项同时「完成并退款」，必须只有一笔成功；另一笔不是排队等，
 * 而是拿到锁后看到额度已用，返回 81004。
 */
class AftersaleConcurrencyTest extends ConcurrencyTestBase {

    @Autowired
    private BuyerAftersaleService buyerAftersales;

    @Autowired
    private SellerAftersaleService sellerAftersales;

    @Test
    @DisplayName("同一订单项两笔售后同时完成退款：只有一笔成功，退款金额只落一次")
    void concurrentRefundsOnSameItemSucceedOnce() throws Exception {
        BuyerAccount buyer = createBuyer();
        Goods goods = publishedGoods(5);
        Order order = completedOrder(buyer, goods);
        String itemId = orderItems.findByOrder_IdOrderByGoodsNameAscIdAsc(order.getId())
                .get(0)
                .getId();

        String first = apply(buyer, itemId, "第一次申请");
        String second = apply(buyer, itemId, "第二次申请");

        List<String> outcomes = runConcurrently(2, index ->
                () -> completeOutcome(index == 0 ? first : second));

        assertThat(outcomes).containsExactlyInAnyOrder("OK", "AFTERSALE_REFUND_DUPLICATED");
        assertThat(aftersales.countByOrderItemIdAndStatusAndResultIn(
                        itemId,
                        AftersaleStatus.COMPLETED,
                        List.of(AftersaleResult.RETURN_AND_REFUND, AftersaleResult.REFUND_ONLY)))
                .isEqualTo(1);
    }

    private Order completedOrder(BuyerAccount buyer, Goods goods) {
        Order shipped = shippedOrder(buyer, goods, 1);
        buyerOrders.confirmReceipt(buyer.getId(), shipped.getId());
        return orders.findById(shipped.getId()).orElseThrow();
    }

    private String apply(BuyerAccount buyer, String itemId, String title) {
        AftersaleAppliedData applied = buyerAftersales.apply(buyer.getId(),
                new AftersaleApplyRequest(itemId, title, "并发用例：同一订单项两笔退款。", List.of()));
        return applied.aftersaleId();
    }

    private String completeOutcome(String aftersaleId) {
        try {
            sellerAftersales.complete(aftersaleId,
                    new AftersaleCompleteRequest("REFUND_ONLY"));
            return "OK";
        } catch (BusinessException exception) {
            return exception.getErrorCode().name();
        }
    }
}
