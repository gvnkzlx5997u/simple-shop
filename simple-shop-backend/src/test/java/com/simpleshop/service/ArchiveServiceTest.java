package com.simpleshop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.simpleshop.persistence.enums.IntentionStatus;
import com.simpleshop.persistence.repository.IntentionHistoryRepository;
import com.simpleshop.persistence.repository.TradeHistoryRepository;
import com.simpleshop.service.exception.BusinessException;
import com.simpleshop.service.exception.ErrorCode;
import com.simpleshop.testing.ShopFixtures;

/**
 * 归档的<b>逐列核对</b>与两条不变量（{@code INV-05}／{@code INV-06}）的专项验证——S7 的验收凭据（方案 §3.3.6、§8.3）。
 *
 * <h2>⚠️ 为什么单独一个类：现有用例都只覆盖了归档的「一部分」</h2>
 * <table border="1">
 *   <caption>归档证据的分布（S7 之前）</caption>
 *   <tr><th>已有用例</th><th>它验证了什么</th><th>它的缺口</th></tr>
 *   <tr><td>{@code ArchiveAndUtcTest}（数据层）</td>
 *       <td>历史<b>实体</b>与字段拷贝的口径、时间列不被回调覆盖</td>
 *       <td>它<b>手工拼</b>历史行、<b>不删除</b>当前行——<b>根本不经过</b> {@link ArchiveService}</td></tr>
 *   <tr><td>{@code SellerGoodsApiTest}（S4）</td>
 *       <td>{@code I11-09} 下架后历史表里几个关键列、{@code 30005} 会拒绝</td>
 *       <td>只核对了部分列；且只覆盖 <b>offline</b> 一条路径</td></tr>
 *   <tr><td>{@code SellerIntentionApiTest}（S6）</td>
 *       <td>{@code I11-12} 标记成功后的 {@code result=sold}、当前表清空</td>
 *       <td>同上，只核对了部分列</td></tr>
 * </table>
 * <p>本类补上三件事：① <b>两条路径都逐列核对</b>（历史商品 12 列、历史意向 8 列）；
 * ② <b>整体回滚</b>——特别是「流水已插入、随后归档被 {@code INV-05} 拒绝」这一条
 * （全项目只有它是「先写后拒」，是最容易留下脏数据的地方）；
 * ③ {@code archive} 的<b>写入顺序</b>为什么是硬约束（用物理外键给出证据，而不是靠注释）。
 *
 * <h2>⚠️ 本类的守护能力是<b>实测</b>过的，不是声称的（S7 做的变异验证）</h2>
 * <p>一个「全绿」的新测试类并不能证明它守得住东西。S7 因此对 {@link ArchiveService} 做了三次
 * <b>定向变异</b>，看本类会不会响：
 * <table border="1">
 *   <caption>变异验证结果</caption>
 *   <tr><th>变异</th><th>结果</th><th>结论</th></tr>
 *   <tr><td>把 {@code saveAndFlush(历史商品)} <b>挪到</b>意向写入之后（顺序写反）</td>
 *       <td><b>6/10 失败</b>（外键/找不到历史商品）</td>
 *       <td>§8.3 #11 的「顺序」<b>确实被守住</b></td></tr>
 *   <tr><td>{@code deleteAll} → {@code deleteAllInBatch}</td>
 *       <td><b>6/10 失败</b>（{@code TransientObjectException}）</td>
 *       <td>第二条「致命顺序」也被守住</td></tr>
 *   <tr><td>{@code saveAndFlush} → {@code save}（<b>顺序不变</b>）</td>
 *       <td><b>10/10 通过</b></td>
 *       <td>这个差别<b>不可观测</b>——故 {@code ArchiveService} 的注释已相应地改准确：
 *           用 {@code saveAndFlush} 是为了<b>不依赖</b> Hibernate 的 INSERT 排序，
 *           而不是因为它能修掉某个已观测到的失败</td></tr>
 * </table>
 * <p>第三条同样重要：<b>不能因为一个测试会失败就断言某种写法「必须」如此</b>——
 * 差异若不可观测，就该如实说成「显式保证」而不是「必需的修复」。
 *
 * <h2>⚠️ 为什么直接调 Service 而不是走 HTTP</h2>
 * <p>本类验证的是<b>归档的数据结果</b>（每一列的取值），不是传输契约——那些由
 * {@code SellerGoodsApiTest}／{@code SellerIntentionApiTest} 覆盖。直接调 Service 少一层噪声，
 * 且能让「归档失败 → 整体回滚」的断言直接检查事务边界。
 * <p>两条路径都是<b>真实业务入口</b>（{@link SellerGoodsService#takeGoodsOffline()} 与
 * {@link SellerIntentionService#markTradeSuccess(String)}），没有绕过任何业务规则。
 */
@SpringBootTest
@ActiveProfiles("test")
class ArchiveServiceTest {

    /** 历史商品表的完整列集（顺序无关；用 {@code containsExactlyInAnyOrder} 核对）。 */
    private static final List<String> GOODS_HISTORY_COLUMNS = List.of(
            "id", "name", "description", "pic_url", "price", "status", "freeze_by",
            "create_at", "update_at", "trade_start", "trade_end", "result");

    /** 历史意向表的完整列集——⚠️ 刻意<b>没有</b> {@code queue_order} 与 {@code token}。 */
    private static final List<String> INTENTION_HISTORY_COLUMNS = List.of(
            "id", "fk_goods_history_id", "create_at", "name", "tel", "status", "fail_type", "fail_reason");

    /** 「1:1 继承」的列（其余列由归档改写，见 {@code EntityFactory#newGoodsHistory}）。 */
    private static final List<String> INHERITED_GOODS_COLUMNS = List.of(
            "id", "name", "description", "pic_url", "price", "create_at", "trade_start");

    private static final String DESC = "手冲咖啡豆 250g";
    private static final String PIC_URL = "/images/2026/10/archive-case.jpg";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SellerGoodsService sellerGoodsService;

    @Autowired
    private SellerIntentionService sellerIntentionService;

    @Autowired
    private IntentionHistoryRepository intentionHistoryRepository;

    @Autowired
    private TradeHistoryRepository tradeHistoryRepository;

    private ShopFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new ShopFixtures(jdbcTemplate);
        fixtures.cleanGoodsState();
        fixtures.resetSequence();
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanGoodsState();
    }

    // =========================================================================
    // 逐列核对：手动下架（offline）
    // =========================================================================

    @Test
    @DisplayName("下架归档：历史商品 12 列逐列核对（继承 7 列 / 归档改写 5 列）")
    void offlineArchiveCopiesEveryGoodsColumn() {
        String goodsId = insertGoodsWithAllColumnsFilled();
        // 发布/更新时间设为一天前：这样「update_at 是否被复制的原值」才可判别
        LocalDateTime oneDayAgo = LocalDateTime.now(ZoneOffset.UTC)
                .minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update("update simpleshop_goods set create_at = ?, update_at = ? where id = ?",
                oneDayAgo, oneDayAgo, goodsId);
        // 队列必须为空才能下架：放一条终态意向占位（它同时用于下一个用例之外的场景）
        fixtures.insertIntention(goodsId, "revoked");

        Map<String, Object> before = goodsRow(goodsId);
        LocalDateTime callStart = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(2);

        sellerGoodsService.takeGoodsOffline();

        Map<String, Object> history = goodsHistoryRow(goodsId);
        LocalDateTime callEnd = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(2);

        // ① 列集：不多不少
        assertThat(history.keySet())
                .as("历史商品的列集必须与当前表 1:1 同名")
                .containsExactlyInAnyOrderElementsOf(GOODS_HISTORY_COLUMNS);

        // ② 1:1 继承的 7 列
        Map<String, Object> inherited = new LinkedHashMap<>();
        INHERITED_GOODS_COLUMNS.forEach(column -> inherited.put(column, before.get(column)));
        assertThat(history)
                .as("这 7 列必须逐列继承原值：%s", INHERITED_GOODS_COLUMNS)
                .containsAllEntriesOf(inherited);

        // ③ 归档改写的 5 列（§9.6.1 的「归档例外」+ 当前表恒 NULL 的三列）
        assertThat(history.get("status")).as("归档例外①：恒写 off_sale").isEqualTo("off_sale");
        assertThat(history.get("freeze_by")).as("归档例外②：恒清空").isNull();
        assertThat(history.get("result")).isEqualTo("offline");
        assertThat(history.get("trade_end")).as("下架时间（必填）").isNotNull();
        assertThat(history.get("update_at"))
                .as("update_at ← 归档时间，【不】复制原 update_at")
                .isEqualTo(history.get("trade_end"))
                .isNotEqualTo(oneDayAgo);
        // 时间必须落在本次调用窗口内（不是随手取的其它时刻）
        assertThat((LocalDateTime) history.get("trade_end")).isBetween(callStart, callEnd);
        // 未进入过交易 → trade_start 为空（它是「本次交易开始时间」）
        assertThat(history.get("trade_start")).isNull();
    }

    @Test
    @DisplayName("下架归档：历史意向 8 列逐列核对，外键改名而值不变")
    void offlineArchiveCopiesEveryIntentionColumn() {
        String goodsId = insertGoodsWithAllColumnsFilled();
        String failed = fixtures.insertIntention(goodsId, 1, "failed", "ARCHIVE00001");
        String revoked = fixtures.insertIntention(goodsId, 2, "revoked", "ARCHIVE00002");
        jdbcTemplate.update("update simpleshop_intentions set fail_type = 'voided', fail_reason = ? "
                + "where id = ?", "卖家作废-归档核对", failed);
        jdbcTemplate.update("update simpleshop_intentions set fail_type = 'revoked' where id = ?", revoked);

        Map<String, Object> beforeFailed = intentionRow(failed);
        Map<String, Object> beforeRevoked = intentionRow(revoked);

        sellerGoodsService.takeGoodsOffline();

        for (Map<String, Object> before : List.of(beforeFailed, beforeRevoked)) {
            String intentionId = (String) before.get("id");
            Map<String, Object> history = intentionHistoryRow(intentionId);

            assertThat(history.keySet())
                    .as("历史意向的列集：⚠️ 不含 queue_order 与 token（DEC-DB-05、9-S）")
                    .containsExactlyInAnyOrderElementsOf(INTENTION_HISTORY_COLUMNS);

            // 逐列：除外键改名外全部继承
            assertThat(history.get("id")).as("9-G：沿用原 ID（前缀仍为 I）").isEqualTo(before.get("id"));
            assertThat(history.get("fk_goods_history_id"))
                    .as("fk_good_id 改名为 fk_goods_history_id，【值不变】").isEqualTo(goodsId);
            assertThat(history.get("create_at"))
                    .as("create_at 继承原值——它也是历史名单的排序键（11-H）")
                    .isEqualTo(before.get("create_at"));
            assertThat(history.get("name")).isEqualTo(before.get("name"));
            assertThat(history.get("tel")).isEqualTo(before.get("tel"));
            assertThat(history.get("status")).isEqualTo(before.get("status"));
            assertThat(history.get("fail_type")).isEqualTo(before.get("fail_type"));
            assertThat(history.get("fail_reason")).isEqualTo(before.get("fail_reason"));
        }
        assertThat(intentionHistoryRepository.countByGoodsHistoryId(goodsId)).isEqualTo(2);
    }

    // =========================================================================
    // 逐列核对：标记成功（sold）
    // =========================================================================

    @Test
    @DisplayName("标记成功归档：result=sold、trade_start 继承、update_at=trade_end，且 INV-06 恰 1 条 succeeded")
    void soldArchiveWritesSoldResultAndInheritsTradeStart() {
        String goodsId = insertGoodsWithAllColumnsFilled();
        String head = fixtures.insertIntention(goodsId, 1, "queued", "SOLDARCH0001");
        fixtures.insertIntention(goodsId, 2, "queued", "SOLDARCH0002");

        sellerIntentionService.enterTrade(head);
        LocalDateTime tradeStart = (LocalDateTime) goodsRow(goodsId).get("trade_start");
        assertThat(tradeStart).as("进入交易时写入 trade_start").isNotNull();

        sellerIntentionService.markTradeSuccess(head);

        Map<String, Object> history = goodsHistoryRow(goodsId);
        assertThat(history.keySet()).containsExactlyInAnyOrderElementsOf(GOODS_HISTORY_COLUMNS);
        assertThat(history.get("status")).isEqualTo("off_sale");
        assertThat(history.get("freeze_by")).as("归档例外②：交易冻结来源也被清空").isNull();
        assertThat(history.get("result")).isEqualTo("sold");
        assertThat(history.get("trade_start"))
                .as("已进入交易的商品：trade_start 继承原值，不是 null").isEqualTo(tradeStart);
        assertThat(history.get("update_at"))
                .as("update_at 与 trade_end 同为归档（成交）时间").isEqualTo(history.get("trade_end"));
        assertThat((LocalDateTime) history.get("trade_end")).isAfterOrEqualTo(tradeStart);

        // INV-06：历史意向里 succeeded 恰 1 条，其余为 sold_out 连带失败
        assertThat(fixtures.count("select count(*) from simpleshop_intentions_history "
                + "where fk_goods_history_id = ? and status = 'succeeded'", goodsId)).isEqualTo(1);
        assertThat(fixtures.count("select count(*) from simpleshop_intentions_history "
                + "where fk_goods_history_id = ? and status = 'failed' and fail_type = 'sold_out'",
                goodsId)).isEqualTo(1);
    }

    @Test
    @DisplayName("INV-06：两条归档路径的成功数——sold 恰 1、offline 恰 0")
    void inv06SucceededCountDependsOnArchivePath() {
        // 路径一：下架（没有人成交）
        String offlineGoodsId = insertGoodsWithAllColumnsFilled();
        fixtures.insertIntention(offlineGoodsId, "revoked");
        sellerGoodsService.takeGoodsOffline();
        assertThat(succeededInHistory(offlineGoodsId))
                .as("手动下架：没有任何意向进入过交易，故 succeeded 必为 0").isZero();

        // 路径二：标记成功（恰一人成交）
        fixtures.cleanGoodsState();
        fixtures.resetSequence();
        String soldGoodsId = insertGoodsWithAllColumnsFilled();
        String head = fixtures.insertIntention(soldGoodsId, 1, "queued", "INV06SOLD001");
        sellerIntentionService.enterTrade(head);
        sellerIntentionService.markTradeSuccess(head);
        assertThat(succeededInHistory(soldGoodsId))
                .as("标记成功：恰 1 条 succeeded（INV-06）").isEqualTo(1);
    }

    // =========================================================================
    // INV-05：拒绝与【整体回滚】
    // =========================================================================

    @Test
    @DisplayName("⚠️ INV-05：归档被拒时连【已插入的流水】一起回滚（全项目唯一「先写后拒」的路径）")
    void inv05RejectionRollsBackTheAlreadyAppendedTradeFlow() {
        String goodsId = insertGoodsWithAllColumnsFilled();
        String head = fixtures.insertIntention(goodsId, 1, "queued", "ROLLBACK0001");
        String extra = fixtures.insertIntention(goodsId, 2, "queued", "ROLLBACK0002");
        sellerIntentionService.enterTrade(head);

        // 手工把第二条也置为 trading —— 故意违反 INV-04（这是 ShopFixtures 存在的意义：
        // 造出正常业务流程【到不了】的状态，用来验证兜底防线真的在守）。
        // 它会让 archive 的 INV-05 前置校验在【流水已插入、状态已改】之后拒绝归档。
        jdbcTemplate.update("update simpleshop_intentions set status = 'trading' where id = ?", extra);

        assertThatThrownBy(() -> sellerIntentionService.markTradeSuccess(head))
                .as("存在 activity 意向时归档必须被拒（30005）")
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ARCHIVE_PRECONDITION_FAILED);

        // ---- 整体回滚：五处证据 ----
        assertThat(tradeHistoryRepository.countByIntentionId(head))
                .as("① 归档【之前】已追加的那条流水必须被回滚——这是本用例的核心")
                .isZero();
        assertThat(intentionRow(head).get("status"))
                .as("② 本次意向仍应是 trading（succeeded 未提交）").isEqualTo("trading");
        assertThat(intentionRow(extra).get("status"))
                .as("③ 连带失败也没提交").isEqualTo("trading");
        Map<String, Object> goods = goodsRow(goodsId);
        assertThat(goods.get("status")).as("④ 商品仍在冻结，未进入已下架").isEqualTo("frozen");
        assertThat(goods.get("freeze_by")).isEqualTo("trade");
        assertThat(fixtures.countGoods()).as("⑤ 当前表未被清空").isEqualTo(1);
        assertThat(fixtures.countIntentions()).isEqualTo(2);
        assertThat(fixtures.count("select count(*) from simpleshop_goods_history")).isZero();
        assertThat(fixtures.count("select count(*) from simpleshop_intentions_history")).isZero();
    }

    @Test
    @DisplayName("INV-05：下架路径同样被拒——商品仍在售、历史表零行")
    void inv05RejectsOfflineWhenTradingIntentionExists() {
        String goodsId = insertGoodsWithAllColumnsFilled();
        // 「在售却有 trading 意向」：队列（queued）为空，故会通过 20006 那道校验，
        // 一路走到 archive，由 INV-05 兜底拒绝。
        fixtures.insertIntention(goodsId, "trading");

        assertThatThrownBy(() -> sellerGoodsService.takeGoodsOffline())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ARCHIVE_PRECONDITION_FAILED);

        assertThat(goodsRow(goodsId).get("status"))
                .as("被拒后商品必须仍是原状态（无部分归档）").isEqualTo("on_sale");
        assertThat(fixtures.count("select count(*) from simpleshop_goods_history")).isZero();
        assertThat(fixtures.count("select count(*) from simpleshop_intentions_history")).isZero();
    }

    // =========================================================================
    // 计数一致、当前表清空、写入顺序
    // =========================================================================

    @Test
    @DisplayName("归档前后计数一致（25 条意向）+ 当前表彻底清空（硬删除）")
    void archiveCountsMatchAndCurrentTablesAreEmptied() {
        String goodsId = insertGoodsWithAllColumnsFilled();
        // 25 条：既验证「逐条搬迁不丢」，也是 §8.3 #11 的【顺序守护】——
        // 把 saveAndFlush(历史商品) 挪到意向写入之后，本用例会以
        // JpaObjectRetrievalFailure/FK 失败（S7 已实测：那条变异让 6/10 用例变红）。
        int inserted = fixtures.insertIntentions(goodsId, 25, "revoked");
        assertThat(inserted).isEqualTo(25);

        sellerGoodsService.takeGoodsOffline();

        assertThat(fixtures.count("select count(*) from simpleshop_goods_history"))
                .as("历史商品 +1").isEqualTo(1);
        assertThat(intentionHistoryRepository.countByGoodsHistoryId(goodsId))
                .as("历史意向数 = 归档前当前表意向数").isEqualTo(25);
        assertThat(fixtures.countGoods()).as("当前商品表清空").isZero();
        assertThat(fixtures.countIntentions()).as("当前意向表清空").isZero();

        // INV-05 的事后断言：历史表里不得有非终态意向（DEC-DB-14 删掉了数据库侧兜底，
        // 所以这条断言是【唯一】能发现「非终态被静默写进历史」的地方）
        assertThat(intentionHistoryRepository.countActive(
                List.of(IntentionStatus.queued, IntentionStatus.trading)))
                .as("历史意向表里不得存在 queued/trading（INV-05 的事后核验）").isZero();
    }

    @Test
    @DisplayName("⚠️ 归档的写入顺序是硬约束：历史意向若先于历史商品落库，会被物理外键拒绝")
    void historyIntentionCannotBeInsertedBeforeItsGoodsHistory() {
        // 这就是 EntityFactory/ArchiveService 里那句「必须先 saveAndFlush 历史商品」的
        // 【物理依据】——不是风格建议，而是数据库会直接拒绝。
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into simpleshop_intentions_history "
                        + "(id, fk_goods_history_id, create_at, name, tel, status) "
                        + "values (?,?,?,?,?,?)",
                "ARCHFK000001", "GNO-SUCH-GOODS", LocalDateTime.now(ZoneOffset.UTC),
                "归档孤儿", "13800000000", "revoked"))
                .as("fk_goods_history_id 上的物理外键必须存在且生效")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // 恒 NULL 与流水条数
    // =========================================================================

    @Test
    @DisplayName("当前表的 result / trade_end 恒 NULL（唯一可观测窗口：标记失败后商品仍在）")
    void currentTableResultAndTradeEndAreNeverWritten() {
        String goodsId = insertGoodsWithAllColumnsFilled();
        String head = fixtures.insertIntention(goodsId, 1, "queued", "NULLRESULT01");
        sellerIntentionService.enterTrade(head);

        // 「标记失败」是唯一一个『商品留在原表』的交易结果操作 —— 于是它成了
        // 「归档路径从不写当前表 result/trade_end」这句话唯一能被观测到的窗口。
        sellerIntentionService.markTradeFailure(head, "voided", "买家放弃");

        Map<String, Object> goods = goodsRow(goodsId);
        assertThat(goods.get("status")).isEqualTo("on_sale");
        assertThat(goods.get("result"))
                .as("DEC-DB-11／9-H：当前表 result 恒为 NULL，任何路径都不得写它").isNull();
        assertThat(goods.get("trade_end"))
                .as("当前表 trade_end 也一律不写（值只出现在历史表）").isNull();
        assertThat(goods.get("trade_start"))
                .as("trade_start 是唯一被写入的交易时间列（留给下一次进入交易覆盖）").isNotNull();
        assertThat(fixtures.count("select count(*) from simpleshop_goods_history"))
                .as("标记失败不归档").isZero();
    }

    @Test
    @DisplayName("流水条数：同一意向 N 次交易结果 ⇒ 恰好 N 条；手动下架 ⇒ 0 条")
    void tradeFlowCountFollowsTheNumberOfTradeResults() {
        String goodsId = insertGoodsWithAllColumnsFilled();
        String intention = fixtures.insertIntention(goodsId, 1, "queued", "FLOWCOUNT001");

        // 第一次交易：进入 → 重新排队（失败，1 条流水）
        sellerIntentionService.enterTrade(intention);
        sellerIntentionService.markTradeFailure(intention, "requeued", "买家要求改期");
        assertThat(tradeHistoryRepository.countByIntentionId(intention)).isEqualTo(1);

        // 第二次交易：进入 → 作废（失败，第 2 条流水）
        sellerIntentionService.enterTrade(intention);
        sellerIntentionService.markTradeFailure(intention, "voided", "再次失败");
        assertThat(tradeHistoryRepository.countByIntentionId(intention))
                .as("N 次「标记交易结果」⇒ 恰好 N 条流水（BR-22／§9.7.2）").isEqualTo(2);

        // 两次的失败类型各自留痕（这是 S8 的 trades[] 要逐条展示的内容）。
        //
        // ⚠️⚠️ 这里【只能】断言「两条都在、内容正确」，【不能】断言它们谁在前——
        //    实测（本用例第一版就是按顺序断言的）：两次 enterTrade 相隔毫秒，
        //    而 trade_start 是 DATETIME（**秒**精度），两行的 trade_start 完全相同，
        //    于是 `order by trade_start asc` 的两行顺序是**不确定的**，用例偶发失败。
        //    这不是测试的毛病，而是口径事实：**同一秒内的两次交易无法靠 trade_start 分先后**
        //    （已知问题，已登记在 §8.12 的 O-1；S8 的 I11-16 `trades[]` 排序会正面遇到它）。
        List<Map<String, Object>> flows = jdbcTemplate.queryForList(
                "select result, fail_type, fail_reason from simpleshop_trade_history "
                        + "where fk_intention_id = ?", intention);
        assertThat(flows).hasSize(2);
        assertThat(flows).allSatisfy(flow -> assertThat(flow).containsEntry("result", "failed"));
        assertThat(flows).extracting(flow -> flow.get("fail_type"))
                .as("每一次失败都必须逐条可见（BR-22、澄清 Q15）")
                .containsExactlyInAnyOrder("requeued", "voided");
        assertThat(flows).extracting(flow -> flow.get("fail_reason"))
                .containsExactlyInAnyOrder("买家要求改期", "再次失败");

        // 手动下架：没有任何意向进入过交易 → 一条流水都不产生
        jdbcTemplate.update("delete from simpleshop_trade_history");
        jdbcTemplate.update("update simpleshop_intentions set status = 'failed', fail_type = 'voided' "
                + "where id = ?", intention);
        sellerGoodsService.takeGoodsOffline();
        assertThat(fixtures.count("select count(*) from simpleshop_trade_history"))
                .as("§9.9.5：手动下架不产生任何流水").isZero();
    }

    // =========================================================================
    // 用例辅助
    // =========================================================================

    /** 造一件「所有可空列都有值」的在售商品，使逐列核对有区分度。 */
    private String insertGoodsWithAllColumnsFilled() {
        String goodsId = fixtures.insertGoods("on_sale", null, "归档核对商品", "9.90");
        jdbcTemplate.update("update simpleshop_goods set description = ?, pic_url = ? where id = ?",
                DESC, PIC_URL, goodsId);
        return goodsId;
    }

    private Map<String, Object> goodsRow(String goodsId) {
        return jdbcTemplate.queryForMap("select * from simpleshop_goods where id = ?", goodsId);
    }

    private Map<String, Object> goodsHistoryRow(String goodsId) {
        return jdbcTemplate.queryForMap("select * from simpleshop_goods_history where id = ?", goodsId);
    }

    private Map<String, Object> intentionRow(String intentionId) {
        return jdbcTemplate.queryForMap("select * from simpleshop_intentions where id = ?", intentionId);
    }

    private Map<String, Object> intentionHistoryRow(String intentionId) {
        return jdbcTemplate.queryForMap(
                "select * from simpleshop_intentions_history where id = ?", intentionId);
    }

    private int succeededInHistory(String goodsId) {
        return fixtures.count("select count(*) from simpleshop_intentions_history "
                + "where fk_goods_history_id = ? and status = 'succeeded'", goodsId);
    }
}
