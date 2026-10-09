package com.simpleshop;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.simpleshop.persistence.entity.Goods;
import com.simpleshop.persistence.entity.Intention;
import com.simpleshop.persistence.entity.QueueSequence;
import com.simpleshop.persistence.enums.GoodsResult;
import com.simpleshop.persistence.enums.GoodsStatus;
import com.simpleshop.persistence.enums.IntentionStatus;
import com.simpleshop.persistence.repository.GoodsRepository;
import com.simpleshop.persistence.repository.IntentionHistoryRepository;
import com.simpleshop.persistence.repository.IntentionRepository;
import com.simpleshop.persistence.repository.QueueSequenceRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 不变量断言与并发序号。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code INV-01}：{@code status ∈ {on_sale, frozen}} 行数 ≤ 1</li>
 *   <li>{@code INV-02}：同一商品 {@code trading} 意向数 ≤ 1</li>
 *   <li>{@code INV-05}：历史意向表中非终态记录数恒为 0</li>
 *   <li>{@code INV-06}：同一商品 {@code succeeded} 意向数 ∈ {0, 1}</li>
 *   <li>{@code INV-08}：{@code queued} 意向的口令码查询必然返回有效</li>
 *   <li>并发序号：并发提交 10 条 → 意向行数 = 10、{@code queue_order} 互不重复（{@code NFR-01}、{@code 12-P3}）</li>
 * </ul>
 *
 * <p><b>并发用例不加类级 {@code @Transactional}</b>：每个线程必须持有各自独立的真实事务，
 * 否则 {@code SELECT ... FOR UPDATE} 无法真正并发（同一事务内重入不会阻塞），
 * 测试也就失去了意义。清理仍由基类的 {@code @AfterEach} 按标记完成。
 */
@DisplayName("不变量与并发序号")
class InvariantAndConcurrencyTest extends DataLayerTestBase {

    @Autowired private GoodsRepository goodsRepository;
    @Autowired private IntentionRepository intentionRepository;
    @Autowired private IntentionHistoryRepository intentionHistoryRepository;
    @Autowired private QueueSequenceRepository queueSequenceRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    /** 并发线程数：并发提交 10 条。 */
    private static final int CONCURRENT_SUBMISSIONS = 10;

    // -------------------------------------------------------------------------
    // INV-01
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("INV-01：在售/冻结商品至多 1 行（常规路径，应用层保障）")
    void inv01_atMostOneActiveGoods() {
        // 常规路径：Service 会先用 existsByStatusIn 判定，再插入
        assertThat(goodsRepository.existsByStatusIn(
                List.of(GoodsStatus.on_sale, GoodsStatus.frozen))).isFalse();

        goodsRepository.saveAndFlush(newGoods(GoodsStatus.on_sale));

        assertThat(goodsRepository.existsByStatusIn(
                List.of(GoodsStatus.on_sale, GoodsStatus.frozen)))
                .as("插入后判定入口应返回 true，Service 据此拒绝第二次发布")
                .isTrue();

        Integer activeRows = jdbcTemplate.queryForObject(
                "select count(*) from simpleshop_goods where status in ('on_sale','frozen')", Integer.class);
        assertThat(activeRows).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // INV-02 / INV-06
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("INV-02：同一商品 trading 意向至多 1 条；INV-06：succeeded ∈ {0,1}")
    void inv02AndInv06_countsWithinBounds() {
        Goods goods = goodsRepository.saveAndFlush(newGoods(GoodsStatus.frozen));

        intentionRepository.saveAndFlush(newIntention(goods, 601, IntentionStatus.queued, nextToken()));
        intentionRepository.saveAndFlush(newIntention(goods, 602, IntentionStatus.trading, nextToken()));
        intentionRepository.saveAndFlush(newIntention(goods, 603, IntentionStatus.queued, nextToken()));

        long trading = intentionRepository.countByGoodsIdAndStatus(goods.getId(), IntentionStatus.trading);
        long succeeded = intentionRepository.countByGoodsIdAndStatus(goods.getId(), IntentionStatus.succeeded);

        assertThat(trading).as("INV-02 上界").isLessThanOrEqualTo(1);
        assertThat(succeeded).as("INV-06 取值域").isIn(0L, 1L);
        assertThat(trading).isEqualTo(1);

        // 归档前置校验入口：仍有 queued/trading 时应能报出 > 0
        long active = intentionRepository.countActiveByGoodsId(goods.getId(), activeStatuses());
        assertThat(active).as("归档前存在活动意向，应 > 0 从而被 Service 拒绝").isGreaterThan(0);
    }

    @Test
    @DisplayName("INV-02：走商品行锁进入交易，第二次进入被业务判定拦下（锁内重查计数）")
    void inv02_secondTradingEntryIsPreventedByRowLockProtocol() {
        Goods goods = goodsRepository.saveAndFlush(newGoods(GoodsStatus.on_sale));
        Intention first = intentionRepository.saveAndFlush(
                newIntention(goods, 611, IntentionStatus.queued, nextToken()));
        intentionRepository.saveAndFlush(newIntention(goods, 612, IntentionStatus.queued, nextToken()));

        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 第一次「进入交易」：按 §8.2 契约先锁商品行，再校验、再改状态
        tx.executeWithoutResult(status -> {
            Goods locked = goodsRepository.findByIdForUpdate(goods.getId()).orElseThrow();
            long trading = intentionRepository.countByGoodsIdAndStatus(locked.getId(), IntentionStatus.trading);
            assertThat(trading).isZero();
            Intention target = intentionRepository.findByIdForUpdate(first.getId()).orElseThrow();
            target.setStatus(IntentionStatus.trading);
            locked.setStatus(GoodsStatus.frozen);
        });

        // 第二次「进入交易」：同一契约下，锁内计数已为 1，Service 应据此拒绝
        Long tradingSeenInsideLock = tx.execute(status -> {
            Goods locked = goodsRepository.findByIdForUpdate(goods.getId()).orElseThrow();
            assertThat(locked.getStatus()).as("商品已被第一次进入交易置为 frozen").isEqualTo(GoodsStatus.frozen);
            return intentionRepository.countByGoodsIdAndStatus(locked.getId(), IntentionStatus.trading);
        });

        assertThat(tradingSeenInsideLock)
                .as("锁内可见 1 条 trading，故第二次进入交易会被拒绝")
                .isEqualTo(1L);
        assertThat(intentionRepository.countByGoodsIdAndStatus(goods.getId(), IntentionStatus.trading))
                .isEqualTo(1L);
    }

    // -------------------------------------------------------------------------
    // INV-05
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("INV-05：历史意向表中终态之外的记录数恒为 0")
    void inv05_historyHoldsOnlyTerminalIntentions() {
        assertThat(intentionHistoryRepository.countActive(activeStatuses()))
                .as("空表时恒为 0").isZero();

        Goods goods = goodsRepository.saveAndFlush(newGoods(GoodsStatus.frozen));
        Intention done = intentionRepository.saveAndFlush(
                newIntention(goods, 621, IntentionStatus.failed, nextToken()));
        intentionRepository.flush();

        var goodsHistory = newGoodsHistory(goods, utcNow(), GoodsResult.offline);
        jdbcTemplate.update("insert into simpleshop_goods_history "
                        + "(id, name, price, status, create_at, update_at, trade_end, result) "
                        + "values (?,?,?,?,?,?,?,?)",
                goodsHistory.getId(), goodsHistory.getName(), goodsHistory.getPrice(), "off_sale",
                goodsHistory.getCreateAt(), goodsHistory.getUpdateAt(), goodsHistory.getTradeEnd(), "offline");
        jdbcTemplate.update("insert into simpleshop_intentions_history "
                        + "(id, fk_goods_history_id, create_at, name, tel, status, fail_type, fail_reason) "
                        + "values (?,?,?,?,?,?,?,?)",
                done.getId(), goods.getId(), done.getCreateAt(), done.getName(), done.getTel(),
                "failed", "voided", null);

        assertThat(intentionHistoryRepository.countActive(activeStatuses()))
                .as("归档后仍应恒为 0——这是 INV-05 唯一的事后观测手段").isZero();
        assertThat(intentionHistoryRepository.countByGoodsHistoryId(goods.getId())).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // INV-08
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("INV-08：queued 意向的口令码查询必然返回有效")
    void inv08_queuedTokenIsAlwaysResolvable() {
        Goods goods = goodsRepository.saveAndFlush(newGoods(GoodsStatus.on_sale));
        String token = nextToken();
        Intention queued = intentionRepository.saveAndFlush(
                newIntention(goods, 631, IntentionStatus.queued, token));
        intentionRepository.flush();
        String intentionId = queued.getId();

        assertThat(queued.getStatus().isTokenValid()).as("queued 的口令码应有效").isTrue();

        var found = intentionRepository.findByToken(token);
        assertThat(found).as("INV-08：凭口令码必须能查到该意向").isPresent();
        assertThat(found.get().getId()).isEqualTo(intentionId);
        assertThat(found.get().getStatus()).isEqualTo(IntentionStatus.queued);

        // 口令码不随「重排队」变更：重排队只刷新 queue_order，create_at 与 token 保持原值
        queued.setQueueOrder(632);
        intentionRepository.saveAndFlush(queued);

        var afterRequeue = intentionRepository.findByToken(token).orElseThrow();
        assertThat(afterRequeue.getId()).isEqualTo(intentionId);
        assertThat(afterRequeue.getToken()).isEqualTo(token);
        assertThat(afterRequeue.getStatus().isTokenValid()).isTrue();
    }

    // -------------------------------------------------------------------------
    // 并发序号（NFR-01、12-P3）
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("并发提交 10 条：意向行数 = 10 且 queue_order 互不重复")
    void concurrentSubmissionsProduceUniqueQueueOrders() throws Exception {
        Goods goods = goodsRepository.saveAndFlush(newGoods(GoodsStatus.on_sale));
        String goodsId = goods.getId();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        // 固定事务超时，避免行锁等待时测试无限挂起
        tx.setTimeout(20);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_SUBMISSIONS);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        AtomicLong failures = new AtomicLong();

        try {
            for (int i = 0; i < CONCURRENT_SUBMISSIONS; i++) {
                final String token = TEST_MARKER + "CC" + i;
                futures.add(pool.submit(() -> {
                    startGate.await(30, TimeUnit.SECONDS);
                    try {
                        // 严格按 §7.8 / §8.3 的契约与加锁顺序：
                        // ① 序号表行锁 → 取 next → ② 插入意向
                        return tx.execute(status -> {
                            QueueSequence seq = queueSequenceRepository
                                    .findByIdForUpdate(QueueSequence.FIXED_ID)
                                    .orElseThrow(() -> new IllegalStateException("队列序号行缺失"));
                            long next = seq.getCurrentValue() + 1;
                            seq.setCurrentValue(next);
                            queueSequenceRepository.save(seq);

                            Intention intention = newIntention(
                                    goodsRepository.findById(goodsId).orElseThrow(), (int) next,
                                    IntentionStatus.queued, token);
                            intentionRepository.saveAndFlush(intention);
                            return next;
                        });
                    } catch (Exception e) {
                        failures.incrementAndGet();
                        return -1L;
                    }
                }));
            }

            startGate.countDown();   // 尽量让 10 个线程同时冲进去

            Set<Long> assigned = new HashSet<>();
            for (Future<Long> future : futures) {
                Long next = future.get(60, TimeUnit.SECONDS);
                if (next != null && next > 0) {
                    assertThat(assigned.add(next))
                            .as("queue_order 不应重复，重复值 = " + next)
                            .isTrue();
                }
            }

            assertThat(failures.get()).as("并发提交不应丢单").isZero();
            assertThat(assigned).as("应分配出 10 个互不相同的序号").hasSize(CONCURRENT_SUBMISSIONS);
            assertThat(assigned).as("序号应为 1..10 的连续区间").containsExactlyInAnyOrderElementsOf(
                    java.util.stream.LongStream.rangeClosed(1, CONCURRENT_SUBMISSIONS)
                            .boxed().toList());

            List<Intention> rows = intentionRepository.findByGoodsIdOrderByQueueOrderAsc(goodsId);
            assertThat(rows).as("意向行数应等于提交次数").hasSize(CONCURRENT_SUBMISSIONS);
            assertThat(rows).extracting(Intention::getQueueOrder).doesNotHaveDuplicates();

            Long seqAfter = jdbcTemplate.queryForObject(
                    "select current_value from simpleshop_queue_sequence where id = 1", Long.class);
            assertThat(seqAfter).as("序号表应推进到 10").isEqualTo((long) CONCURRENT_SUBMISSIONS);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
