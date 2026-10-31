package com.simpleshop.intention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.common.time.DatabaseTimeProvider;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.history.entity.GoodsHistory;
import com.simpleshop.history.entity.IntentionHistory;
import com.simpleshop.history.enums.IntentionResult;
import com.simpleshop.intention.entity.GoodsQueueSequence;
import com.simpleshop.intention.entity.Intention;
import com.simpleshop.intention.enums.FailType;
import com.simpleshop.intention.enums.IntentionStatus;
import com.simpleshop.testing.DataLayerTestBase;

class IntentionDataTest extends DataLayerTestBase {

    @Test
    @DisplayName("队列序号按商品递增")
    void queueSequenceIncrementsPerGoods() {
        Goods goods = createGoods(createCategory());
        GoodsQueueSequence sequence = queueSequences.saveAndFlush(
                GoodsQueueSequence.forGoods(goods.getId()));

        assertThat(sequence.nextValue()).isEqualTo(1);
        assertThat(sequence.nextValue()).isEqualTo(2);
        queueSequences.saveAndFlush(sequence);

        assertThat(queueSequences.findById(goods.getId()).orElseThrow().getCurrentValue())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("意向可以提交，并可按商品查到队首")
    void submitsIntentionAndFindsQueueHead() {
        Goods goods = createGoods(createCategory());
        BuyerAccount buyer = createBuyer();
        Intention intention = intentions.saveAndFlush(Intention.submit(goods.getId(), buyer, 1));

        flushAndClear();

        assertThat(intentions.findFirstByGoodsIdAndStatusOrderByQueueOrderAsc(
                goods.getId(), IntentionStatus.QUEUED))
                .get()
                .extracting(Intention::getId)
                .isEqualTo(intention.getId());
        assertThat(intentions.countByGoodsIdAndStatusIn(
                goods.getId(), List.of(IntentionStatus.QUEUED, IntentionStatus.TRADING)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("同一商品的队列序号唯一")
    void rejectsDuplicateQueueOrderForSameGoods() {
        Goods goods = createGoods(createCategory());
        BuyerAccount buyer = createBuyer();
        intentions.saveAndFlush(Intention.submit(goods.getId(), buyer, 1));

        assertThatThrownBy(() -> intentions.saveAndFlush(Intention.submit(goods.getId(), buyer, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("不同商品可以使用相同队列序号")
    void allowsSameQueueOrderForDifferentGoods() {
        BuyerAccount buyer = createBuyer();
        Goods first = createGoods(createCategory());
        Goods second = createGoods(createCategory());

        intentions.saveAndFlush(Intention.submit(first.getId(), buyer, 1));
        intentions.saveAndFlush(Intention.submit(second.getId(), buyer, 1));

        assertThat(intentions.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("意向状态迁移写入终态与失败原因")
    void transitionsIntentionToTerminalState() {
        Goods goods = createGoods(createCategory());
        BuyerAccount buyer = createBuyer();
        Intention intention = intentions.saveAndFlush(Intention.submit(goods.getId(), buyer, 1));

        intention.enterTrade();
        intentions.saveAndFlush(intention);
        assertThat(intention.getStatus()).isEqualTo(IntentionStatus.TRADING);

        intention.markFailed(FailType.SELLER_VOIDED);
        intentions.saveAndFlush(intention);
        flushAndClear();

        Intention loaded = intentions.findById(intention.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(IntentionStatus.FAILED);
        assertThat(loaded.getFailType()).isEqualTo(FailType.SELLER_VOIDED);
        assertThat(loaded.getFinishAt()).isNotNull();
    }

    @Test
    @DisplayName("非终态意向不能带终态时间（DB CHECK）")
    void rejectsQueuedIntentionWithFinishAt() {
        Goods goods = createGoods(createCategory());
        BuyerAccount buyer = createBuyer();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into simpleshop_intention "
                        + "(id, fk_goods_id, fk_buyer_id, queue_order, create_at, status, fail_type, finish_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                "I-INVALID", goods.getId(), buyer.getId(), 99,
                LocalDateTime.now(ZoneOffset.UTC), "QUEUED", null,
                LocalDateTime.now(ZoneOffset.UTC)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("历史意向保存买家与商品快照，不保存队列序号")
    void archivesIntentionWithSnapshotsAndWithoutQueueOrder() {
        Goods goods = createGoods(createCategory());
        BuyerAccount buyer = createBuyer();
        LocalDateTime now = DatabaseTimeProvider.utcNow();
        GoodsHistory goodsHistory = goodsHistories.saveAndFlush(GoodsHistory.archive(
                goods.getId(), goods.getName(), goods.getPrice(), 0,
                "一级类别", "二级类别", "[]", "[]", now.minusMinutes(10)));
        IntentionHistory history = IntentionHistory.archive(
                "I" + UUID.randomUUID(),
                goodsHistory,
                goods.getName(),
                buyer.getId(),
                buyer.getUsername(),
                buyer.getPhone(),
                now.minusMinutes(5),
                IntentionResult.FAILED,
                com.simpleshop.history.enums.FailType.SOLD_OUT,
                now);

        intentionHistories.saveAndFlush(history);
        flushAndClear();

        IntentionHistory loaded = intentionHistories.findById(history.getId()).orElseThrow();
        assertThat(loaded.getBuyerUsername()).isEqualTo(buyer.getUsername());
        assertThat(loaded.getBuyerPhone()).isEqualTo(buyer.getPhone());
        assertThat(loaded.getGoodsName()).isEqualTo(goods.getName());
        assertThat(loaded.getResult()).isEqualTo(IntentionResult.FAILED);
        assertThat(loaded.getFailType()).isEqualTo(com.simpleshop.history.enums.FailType.SOLD_OUT);

        Integer queueOrderColumns = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = database() "
                        + "and table_name = 'simpleshop_intention_history' "
                        + "and column_name = 'queue_order'",
                Integer.class);
        assertThat(queueOrderColumns).isZero();
    }
}
