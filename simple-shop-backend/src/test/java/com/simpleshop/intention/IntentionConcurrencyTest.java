package com.simpleshop.intention;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.auth.AuthPrincipal;
import com.simpleshop.common.auth.AuthRole;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.intention.dto.IntentionAcceptedData;
import com.simpleshop.intention.dto.SubmitIntentionRequest;
import com.simpleshop.intention.entity.Intention;
import com.simpleshop.intention.enums.IntentionStatus;
import com.simpleshop.testing.ConcurrencyTestBase;

/**
 * 意向与交易的并发不变量：序号唯一、同商品同时至多一笔交易、
 * 库存不超卖（归档恰好一次）、冻结期间不收新意向。
 */
class IntentionConcurrencyTest extends ConcurrencyTestBase {

    @Test
    @DisplayName("并发提交意向：序号连续唯一，计数器与意向数一致")
    void concurrentSubmissionsGetUniqueQueueOrders() throws Exception {
        int threads = 8;
        Goods goods = publishedGoods(5);
        List<BuyerAccount> buyers = buyers(threads);

        List<Attempt> results = runConcurrently(threads, index -> () ->
                attemptSubmit(buyers.get(index), goods));

        assertThat(results).allMatch(Attempt::ok);
        List<Intention> queued = intentions
                .findByGoodsIdAndStatusOrderByQueueOrderAsc(goods.getId(), IntentionStatus.QUEUED);
        assertThat(queued).hasSize(threads);
        Set<Long> orders = queued.stream().map(Intention::getQueueOrder).collect(Collectors.toSet());
        assertThat(orders).hasSize(threads);
        assertThat(orders).containsExactlyInAnyOrderElementsOf(
                java.util.stream.LongStream.rangeClosed(1, threads).boxed().toList());
        assertThat(queueSequences.findById(goods.getId()).orElseThrow().getCurrentValue())
                .isEqualTo(threads);
    }

    @Test
    @DisplayName("并发进入交易：只有队首成功，商品恰好一笔交易中")
    void onlyQueueHeadEntersTradeConcurrently() throws Exception {
        Goods goods = publishedGoods(3);
        BuyerAccount firstBuyer = createBuyer();
        BuyerAccount secondBuyer = createBuyer();
        Intention first = submit(firstBuyer, goods);
        Intention second = submit(secondBuyer, goods);

        List<Attempt> results = runConcurrently(2, index -> () ->
                attemptTrade(index == 0 ? first.getId() : second.getId()));

        assertThat(results.stream().filter(Attempt::ok)).hasSize(1);
        assertThat(results).anyMatch(result -> !result.ok()
                && (result.code() == 30002 || result.code() == 30004));
        assertThat(goodsRepository.findById(goods.getId()).orElseThrow().getStatus())
                .isEqualTo(GoodsStatus.FROZEN);
        assertThat(intentions.findByGoodsIdAndStatusOrderByQueueOrderAsc(
                goods.getId(), IntentionStatus.TRADING)).hasSize(1);
        assertThat(intentions.findById(first.getId()).orElseThrow().getStatus())
                .isEqualTo(IntentionStatus.TRADING);
        assertThat(intentions.findById(second.getId()).orElseThrow().getStatus())
                .isEqualTo(IntentionStatus.QUEUED);
    }

    @Test
    @DisplayName("并发重复标记成功：归档恰好一次，库存只扣一次")
    void repeatedSuccessArchivesExactlyOnce() throws Exception {
        Goods goods = publishedGoods(1);
        Intention head = submit(createBuyer(), goods);
        tradeService.enterTrade(head.getId());

        List<Attempt> results = runConcurrently(2, index -> () -> attemptSuccess(head.getId()));

        assertThat(results.stream().filter(Attempt::ok)).hasSize(1);
        // 败者要么在意向定位时发现意向已随归档删除（30001），
        // 要么在锁商品行时发现商品行已删除（20011），取决于与归档提交的相对时序。
        assertThat(results).anyMatch(result -> !result.ok()
                && (result.code() == 30001 || result.code() == 20011));
        assertThat(goodsRepository.findById(goods.getId())).isEmpty();
        assertThat(intentions.count()).isZero();
        assertThat(goodsHistories.findById(goods.getId())).isPresent();
        assertThat(goodsHistories.findById(goods.getId()).orElseThrow().getStock()).isZero();
        assertThat(intentionHistories.findByGoodsHistory_IdOrderByCreateAtAsc(goods.getId()))
                .hasSize(1);
    }

    @Test
    @DisplayName("成交归档与提交意向并发：冻结不收新意向，归档无孤儿意向")
    void successAndSubmitDoNotOversellOrLeaveOrphans() throws Exception {
        Goods goods = publishedGoods(1);
        Intention head = submit(createBuyer(), goods);
        tradeService.enterTrade(head.getId());
        BuyerAccount lateBuyer = createBuyer();

        List<Attempt> results = runConcurrently(2, index -> () -> {
            if (index == 0) {
                return attemptSuccess(head.getId());
            }
            return attemptSubmit(lateBuyer, goods);
        });

        assertThat(results.get(0).ok()).isTrue();
        assertThat(results.get(1).ok()).isFalse();
        assertThat(results.get(1).code()).isIn(20019, 20011);
        assertThat(goodsRepository.findById(goods.getId())).isEmpty();
        assertThat(intentions.count()).isZero();
        assertThat(intentionHistories.findByGoodsHistory_IdOrderByCreateAtAsc(goods.getId()))
                .hasSize(1);
    }

    private record Attempt(boolean ok, int code) {
    }

    private List<BuyerAccount> buyers(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(ignored -> createBuyer())
                .toList();
    }

    private Intention submit(BuyerAccount buyer, Goods goods) {
        IntentionAcceptedData accepted = buyerIntentionService.submit(
                new SubmitIntentionRequest(goods.getId()), principal(buyer), unique("ip-"));
        return intentions.findById(accepted.id()).orElseThrow();
    }

    private Attempt attemptSubmit(BuyerAccount buyer, Goods goods) {
        try {
            buyerIntentionService.submit(
                    new SubmitIntentionRequest(goods.getId()), principal(buyer), unique("ip-"));
            return new Attempt(true, 0);
        } catch (BusinessException exception) {
            return new Attempt(false, exception.getErrorCode().code());
        }
    }

    private Attempt attemptTrade(String intentionId) {
        try {
            tradeService.enterTrade(intentionId);
            return new Attempt(true, 0);
        } catch (BusinessException exception) {
            return new Attempt(false, exception.getErrorCode().code());
        }
    }

    private Attempt attemptSuccess(String intentionId) {
        try {
            tradeService.markSuccess(intentionId);
            return new Attempt(true, 0);
        } catch (BusinessException exception) {
            return new Attempt(false, exception.getErrorCode().code());
        }
    }

    private static AuthPrincipal principal(BuyerAccount buyer) {
        return new AuthPrincipal(AuthRole.BUYER, buyer.getId(), buyer.getUsername());
    }
}
