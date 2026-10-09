package com.simpleshop;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.simpleshop.persistence.entity.Goods;
import com.simpleshop.persistence.entity.GoodsHistory;
import com.simpleshop.persistence.entity.Intention;
import com.simpleshop.persistence.entity.IntentionHistory;
import com.simpleshop.persistence.entity.TestEntities;
import com.simpleshop.persistence.entity.TradeHistory;
import com.simpleshop.persistence.enums.GoodsResult;
import com.simpleshop.persistence.enums.GoodsStatus;
import com.simpleshop.persistence.enums.IntentionStatus;
import com.simpleshop.persistence.enums.TradeResult;
import com.simpleshop.persistence.time.DatabaseTimeProvider;

/**
 * 数据层自测的公共基类。
 *
 * <h2>为什么跑在独立库</h2>
 * <p>{@code @ActiveProfiles("test")} 把数据源指向 {@code simple_shop_test}
 * （见 {@code src/test/resources/application-test.yml}），
 * <b>不触碰开发库 simple_shop</b>。该库由连接串自动创建，表由 Flyway 迁移脚本建立——
 * 因此测试顺带验证了迁移脚本本身。
 *
 * <h2>清理策略</h2>
 * <p>本基类<b>不</b>用 {@code @Transactional} 回滚：并发测试需要每个线程各自独立的真实事务
 * （否则线程间互相看不见、也可能共用一个 EntityManager）。因此改为<b>显式清理</b>：
 * <ul>
 *   <li>所有本测试创建的行都带统一标记 {@link #TEST_MARKER}，可按标记精确删除；</li>
 *   <li>{@link #cleanTestData()} 按外键依赖的<b>逆序</b>删除，避免撞上 {@code ON DELETE RESTRICT}；</li>
 *   <li><b>不删</b> V2 种子写入的卖家账号与队列序号行——它们是环境的一部分；</li>
 *   <li>每次清理都把队列序号复位为 0，让用例之间彼此独立、序号从 1 开始。</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class DataLayerTestBase {

    /** 本测试创建的数据统一带此前缀，便于精确清理。 */
    protected static final String TEST_MARKER = "DLT-";

    /** 号码分配器：保证同一用例内多个实体的唯一列（token/queue_order）不互相冲突。 */
    private static final AtomicInteger TOKEN_SEQ = new AtomicInteger(1);

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanTestData() {
        // ⚠️ 注意：simpleshop_trade_history.fk_intention_id **没有物理外键**（刻意为之，9-N），
        // 所以删除意向**不会**连带删除流水，必须显式清理，否则会在测试库里越积越多。
        // 判据用「FK 指向本测试的意向」——这比只看流水 id 更可靠，
        // 因为流水的 id 由 Hibernate 生成，前缀是 TH 而不是 TEST_MARKER。
        jdbcTemplate.update("delete from simpleshop_trade_history where fk_intention_id in "
                + "(select id from (select id from simpleshop_intentions where name like ?) as t)", TEST_MARKER + "%");

        jdbcTemplate.update("delete from simpleshop_intentions_history where name like ?", TEST_MARKER + "%");
        jdbcTemplate.update(
                "delete from simpleshop_intentions_history where fk_goods_history_id in "
                        + "(select id from (select id from simpleshop_goods_history where name like ?) as t)",
                TEST_MARKER + "%");
        jdbcTemplate.update("delete from simpleshop_intentions where name like ?", TEST_MARKER + "%");
        jdbcTemplate.update("delete from simpleshop_goods_history where name like ?", TEST_MARKER + "%");
        jdbcTemplate.update("delete from simpleshop_goods where name like ?", TEST_MARKER + "%");
        jdbcTemplate.update("delete from simpleshop_users where account like ?", TEST_MARKER + "%");

        // 队列序号复位，使各用例的序号分配互不影响
        jdbcTemplate.update("update simpleshop_queue_sequence set current_value = 0 where id = 1");
    }

    // -------------------------------------------------------------------------
    // 断言辅助
    // -------------------------------------------------------------------------

    /**
     * 判断给定动作是否<b>被数据库约束拒绝</b>。
     *
     * <p>刻意不写死异常类型：约束失败向上冒泡时可能是
     * {@code DataIntegrityViolationException}（Spring 已翻译），
     * 也可能是 {@code JpaSystemException}（Hibernate 在 flush 时的包装），
     * 具体形态取决于失败发生在 persist 还是 flush 阶段。
     * 断言真正关心的是「<b>数据库说了不</b>」以及<b>是哪条约束</b>，故：
     * <ul>
     *   <li>先匹配常见的数据访问异常；</li>
     *   <li>否则回退到「异常消息链中含指定约束名」——这比宽泛地接受任意异常更严格，</li>
     * </ul>
     * 从而避免「用 {@code assertThatThrownBy} 但异常类型写错」造成的假失败，
     * 也避免把「任意异常」当作通过。
     *
     * @param action         触发数据库写入的动作
     * @param constraintName 期望出现在错误消息中的约束/索引名
     * @return 实际抛出的异常
     */
    protected static Throwable assertRejectedByDatabase(Runnable action, String constraintName) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (messagesContain(thrown, constraintName)) {
                return thrown;
            }
            throw new AssertionError(
                    "数据库确实拒绝了写入，但错误消息中未出现约束名 <" + constraintName + ">，"
                            + "实际异常：" + thrown, thrown);
        }
        throw new AssertionError("期望数据库拒绝该写入（约束 " + constraintName + "），但写入成功了");
    }

    private static boolean messagesContain(Throwable thrown, String needle) {
        for (Throwable t = thrown; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // 测试数据构造
    // -------------------------------------------------------------------------

    /** 新建一个在售商品（名称带测试标记）。 */
    protected Goods newGoods() {
        return newGoods(GoodsStatus.on_sale);
    }

    protected Goods newGoods(GoodsStatus status) {
        Goods goods = TestEntities.newGoods();
        goods.setName(TEST_MARKER + "商品");
        goods.setDescription(TEST_MARKER + "描述");
        goods.setPrice(new BigDecimal("9.90"));
        goods.setStatus(status);
        return goods;
    }

    /**
     * 新建一条意向。{@code queueOrder} 与 {@code token} 必须唯一。
     *
     * @param token 12 位口令码（测试里用标记 + 序号，保证唯一）
     */
    protected Intention newIntention(Goods goods, int queueOrder, IntentionStatus status, String token) {
        Intention intention = TestEntities.newIntention();
        intention.setGoods(goods);
        intention.setQueueOrder(queueOrder);
        intention.setName(TEST_MARKER + "买家");
        intention.setTel("13800000000");
        intention.setStatus(status);
        intention.setToken(token);
        return intention;
    }

    /** 生成本次调用唯一的口令码（长度 20 以内，符合列宽）。 */
    protected static String nextToken() {
        return TEST_MARKER + "TK" + TOKEN_SEQ.getAndIncrement();
    }

    /** 新建一条历史商品（沿用原商品 ID 与关键字段；{@code tradeEnd} 必填）。 */
    protected GoodsHistory newGoodsHistory(Goods goods, LocalDateTime archivedAt, GoodsResult result) {
        GoodsHistory history = TestEntities.newGoodsHistory();
        history.setId(goods.getId());
        history.setName(goods.getName());
        history.setDescription(goods.getDescription());
        history.setPicUrl(goods.getPicUrl());
        history.setPrice(goods.getPrice());
        history.setStatus(GoodsStatus.off_sale);
        history.setCreateAt(goods.getCreateAt());
        history.setUpdateAt(archivedAt);
        history.setTradeStart(goods.getTradeStart());
        history.setTradeEnd(archivedAt);
        history.setResult(result);
        return history;
    }

    /** 把一条意向按归档口径复制为历史意向（沿用原 ID；不含 queue_order / token）。 */
    protected IntentionHistory toHistory(Intention intention, GoodsHistory goodsHistory) {
        IntentionHistory history = TestEntities.newIntentionHistory();
        history.setId(intention.getId());
        history.setGoodsHistory(goodsHistory);
        history.setCreateAt(intention.getCreateAt());
        history.setName(intention.getName());
        history.setTel(intention.getTel());
        history.setStatus(intention.getStatus());
        history.setFailType(intention.getFailType());
        history.setFailReason(intention.getFailReason());
        return history;
    }

    /** 新建一条交易流水。 */
    protected TradeHistory newTradeHistory(String intentionId, LocalDateTime start, LocalDateTime end,
                                           TradeResult result) {
        TradeHistory trade = TestEntities.newTradeHistory();
        trade.setIntentionId(intentionId);
        trade.setTradeStart(start);
        trade.setTradeEnd(end);
        trade.setResult(result);
        return trade;
    }

    protected static LocalDateTime utcNow() {
        return DatabaseTimeProvider.utcNow();
    }

    /** 终态集合（用于 countNonTerminal / countActive）。 */
    protected static List<IntentionStatus> terminalStatuses() {
        return List.of(IntentionStatus.succeeded, IntentionStatus.failed, IntentionStatus.revoked);
    }

    /** 活动（非终态）集合。 */
    protected static List<IntentionStatus> activeStatuses() {
        return List.of(IntentionStatus.queued, IntentionStatus.trading);
    }
}
