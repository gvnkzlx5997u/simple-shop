package com.simpleshop.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.simpleshop.service.dto.BuyerPrompt;
import com.simpleshop.service.dto.BuyerResult;
import com.simpleshop.service.dto.GoodsData;
import com.simpleshop.service.dto.PasscodeQueryResult;
import com.simpleshop.service.dto.SubmitResultData;
import com.simpleshop.testing.AuditLogCapture;
import com.simpleshop.testing.CsvValues;
import com.simpleshop.testing.ShopFixtures;

/**
 * 买家端意向服务的行为验证（{@code FR-12} ~ {@code FR-22}）——S5 的验收凭据。
 *
 * <p>覆盖方案 §8.3 的「口令码」小节全部条目、{@code INV-03}／{@code INV-07}／{@code INV-08}、
 * 以及 §12 验收口径中的 {@code AC-11}（位次与交易中互斥）、{@code AC-17}（冻结期不可提交，
 * <b>{@code P0}</b>）、{@code AC-18}（队列 1000 上限）。
 *
 * <h2>⚠️ 每个用例用【独立】的限流 key</h2>
 * <p>{@link SubmitRateLimiter} 是 Spring 上下文里的<b>单例</b>，计数按 key 存在内存中、
 * 且<b>跨用例不清理</b>。若多个用例共用同一个 key，先跑的用例把额度用掉，后跑的就会
 * 莫名收到 {@link BuyerPrompt#TOO_MANY_REQUESTS}——一种极难排查的「单独跑绿、全量跑红」。
 * 故这里一律通过 {@link #freshClientKey()} 取新 key。
 *
 * <h2>用例表驱动的两张表</h2>
 * <ul>
 *   <li>{@code buyer-query-cases.csv}：{@code caseName,status,queuedAhead,expectedPrompt,expectedRank}
 *       ——把「意向状态 + 前面排了几人」映射到三态结果与位次；</li>
 *   <li>{@code buyer-submit-cases.csv}：{@code caseName,status,freezeBy,nonTerminalCount,name,tel,expectedPrompt}
 *       ——把「商品状态 + 队列占用 + 字段取值」映射到拒绝码。</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class BuyerIntentionServiceTest {

    /** 用例表中表示「未被拒」的记号（与 {@code IntentionValidatorTest} 一致）。 */
    private static final String PASS = "OK";

    /** 队列上限（{@code NFR-07}、{@code queue.max-size}）。 */
    private static final int QUEUE_MAX_SIZE = 1000;

    private static final AtomicInteger CLIENT_KEYS = new AtomicInteger();

    @Autowired
    private BuyerIntentionService buyerIntentionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    // FR-15 / FR-22 三态查询（用例表）
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0} → {3}")
    @CsvFileSource(resources = "/csv/buyer-query-cases.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("口令码三态与位次用例表")
    void queryByTokenCases(String caseName, String status, int queuedAhead,
                           String expectedPrompt, Integer expectedRank) {
        String goodsId = fixtures.insertGoods("on_sale", null, "GDS-三态用例商品", "10.00");

        // 先排 queuedAhead 个 queued（序号更小），再插目标意向
        for (int i = 0; i < queuedAhead; i++) {
            fixtures.insertIntention(goodsId, i + 1, "queued", ShopFixtures.tokenFor(i + 1));
        }

        PasscodeQueryResult result;
        if (status == null) {
            // 目标意向不存在：用一个形状合法但库里没有的码
            result = buyerIntentionService.queryByToken(ShopFixtures.tokenFor(999));
        } else {
            String token = ShopFixtures.tokenFor(900);
            fixtures.insertIntention(goodsId, 900, status, token);
            result = buyerIntentionService.queryByToken(token);
        }

        assertThat(result.prompt())
                .as("用例「%s」的提示码", caseName)
                .isEqualTo(BuyerPrompt.valueOf(expectedPrompt));

        if (result instanceof PasscodeQueryResult.Active active) {
            assertThat(active.rank()).as("用例「%s」的位次", caseName).isEqualTo(expectedRank);
        } else {
            assertThat(expectedRank).as("非 Active 结果不应期望位次").isNull();
        }
    }

    @Test
    @DisplayName("INV-08：口令码查询接受小写与首尾空格（归一化后才查库）")
    void queryNormalizesInputToken() {
        String goodsId = fixtures.insertGoods("on_sale", null, "GDS-归一化商品", "10.00");
        fixtures.insertIntention(goodsId, 1, "queued", "ABC123DEF456");

        assertThat(buyerIntentionService.queryByToken("ABC123DEF456").prompt())
                .isEqualTo(BuyerPrompt.QUEUED_RANK);
        assertThat(buyerIntentionService.queryByToken("abc123def456").prompt())
                .as("小写输入必须仍能查到（10-G 的归一化）")
                .isEqualTo(BuyerPrompt.QUEUED_RANK);
        assertThat(buyerIntentionService.queryByToken("  abc123def456  ").prompt())
                .as("首尾空格必须被去掉")
                .isEqualTo(BuyerPrompt.QUEUED_RANK);
    }

    @Test
    @DisplayName("⚠️ 格式不符的输入退化为「错误口令」（格式判定由调用方先做，见 §3.3.5）")
    void malformedTokenDegradesToNotFound() {
        // 这是【有意】的降级：不泄漏信息是安全的，但文案会是 M10-05 而不是 M10-28。
        // 下一轮 JSP 必须先调 PasscodeService.isWellFormed，已登记为本轮遗留待办。
        assertThat(buyerIntentionService.queryByToken("abc").prompt())
                .isEqualTo(BuyerPrompt.PASSCODE_NOT_FOUND);
        assertThat(buyerIntentionService.queryByToken("").prompt())
                .isEqualTo(BuyerPrompt.PASSCODE_NOT_FOUND);
        assertThat(buyerIntentionService.queryByToken(null).prompt())
                .isEqualTo(BuyerPrompt.PASSCODE_NOT_FOUND);
    }

    @Test
    @DisplayName("INV-07：商品在售 ⇄ 冻结变化前后，同一口令码的返回结果【不变】")
    void queryResultIsIndependentOfGoodsStatus() {
        String goodsId = fixtures.insertGoods("on_sale", null, "GDS-不变量商品", "10.00");
        fixtures.insertIntention(goodsId, 1, "queued", "INV07TOKEN01");

        PasscodeQueryResult before = buyerIntentionService.queryByToken("INV07TOKEN01");
        assertThat(before.prompt()).isEqualTo(BuyerPrompt.QUEUED_RANK);

        // 手动冻结商品（BR-26/INV-07：判定式只看意向状态，绝不掺入商品状态）
        jdbcTemplate.update("update simpleshop_goods set status = 'frozen', freeze_by = 'manual' where id = ?",
                goodsId);

        PasscodeQueryResult after = buyerIntentionService.queryByToken("INV07TOKEN01");
        assertThat(after.prompt())
                .as("商品冻结不得影响口令码查询结果")
                .isEqualTo(BuyerPrompt.QUEUED_RANK);
        assertThat(after).isEqualTo(before);

        // 再解冻，结果仍不变
        jdbcTemplate.update("update simpleshop_goods set status = 'on_sale', freeze_by = NULL where id = ?",
                goodsId);
        assertThat(buyerIntentionService.queryByToken("INV07TOKEN01")).isEqualTo(before);
    }

    @Test
    @DisplayName("BR-19：撤销后位次【自动前移】（位次是计数派生，无需任何「前移」写操作）")
    void rankAdvancesAfterRevocation() {
        String goodsId = fixtures.insertGoods("on_sale", null, "GDS-位次商品", "10.00");
        fixtures.insertIntention(goodsId, 1, "queued", "RANK00000001");
        fixtures.insertIntention(goodsId, 2, "queued", "RANK00000002");
        String thirdId = fixtures.insertIntention(goodsId, 3, "queued", "RANK00000003");

        assertThat(((PasscodeQueryResult.Active) buyerIntentionService.queryByToken("RANK00000003")).rank())
                .as("第三人排第 3")
                .isEqualTo(3);

        // 撤销第一人（走 Service，验证真实路径）
        assertThat(buyerIntentionService.revokeIntention("RANK00000001").rejectedPrompt()).isNull();

        assertThat(((PasscodeQueryResult.Active) buyerIntentionService.queryByToken("RANK00000003")).rank())
                .as("第一人退出队列后，第三人自动变成第 2 位")
                .isEqualTo(2);
        assertThat(fixtures.intentionRow(thirdId).get("queue_order"))
                .as("⚠️ queue_order 本身【没有】变——变的是计数结果")
                .isEqualTo(3);
    }

    // =========================================================================
    // FR-13 / FR-14 提交意向（用例表）
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0} → {6}")
    @CsvFileSource(resources = "/csv/buyer-submit-cases.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("提交意向的拒绝码用例表（含 AC-17 冻结期、字段校验顺序）")
    void submitIntentionCases(String caseName, String status, String freezeBy, int nonTerminalCount,
                              String name, String tel, String expectedPrompt) {
        String goodsId = null;
        if (status != null) {
            goodsId = fixtures.insertGoods(status, freezeBy, "GDS-提交用例商品", "10.00");
        }
        if (goodsId != null && nonTerminalCount > 0) {
            fixtures.insertIntentions(goodsId, nonTerminalCount, "queued");
        }
        int before = fixtures.countIntentions();

        BuyerResult<SubmitResultData> result = buyerIntentionService.submitIntention(
                CsvValues.expand(name), CsvValues.expand(tel), freshClientKey());

        BuyerPrompt expected = PASS.equals(expectedPrompt) ? null : BuyerPrompt.valueOf(expectedPrompt);
        assertThat(result.rejectedPrompt())
                .as("用例「%s」的拒绝码", caseName)
                .isEqualTo(expected);

        if (expected == null) {
            assertThat(fixtures.countIntentions())
                    .as("成功时必须新增 1 条意向")
                    .isEqualTo(before + 1);
            assertThat(result.orElseNull().token())
                    .as("成功时必须返回 12 位口令码")
                    .matches("[A-Z0-9]{12}");
        } else {
            assertThat(fixtures.countIntentions())
                    .as("⚠️ 被拒时【不得新增意向行】（AC-17／AC-18 的硬要求）")
                    .isEqualTo(before);
        }
    }

    @Test
    @DisplayName("提交成功：序号单调递增、状态 queued、fail 字段为空、createAt 已写、商品状态不变")
    void submitAssignsSequenceAndKeepsGoodsUntouched() {
        String goodsId = fixtures.insertGoods("on_sale", null, "GDS-提交成功商品", "10.00");

        SubmitResultData first = buyerIntentionService
                .submitIntention("张三", "13800000000", freshClientKey()).orElseNull();
        SubmitResultData second = buyerIntentionService
                .submitIntention("李四", "13800000001", freshClientKey()).orElseNull();

        assertThat(first.intentionId()).startsWith("I");
        assertThat(first.token()).isNotEqualTo(second.token());

        var row1 = fixtures.intentionRow(first.intentionId());
        var row2 = fixtures.intentionRow(second.intentionId());
        assertThat(row1.get("queue_order")).isEqualTo(1);
        assertThat(row2.get("queue_order")).isEqualTo(2);
        assertThat(row1.get("status")).isEqualTo("queued");
        assertThat(row1.get("fail_type")).isNull();
        assertThat(row1.get("fail_reason")).isNull();
        assertThat(row1.get("name")).isEqualTo("张三");
        assertThat(row1.get("tel")).isEqualTo("13800000000");
        assertThat(row1.get("create_at"))
                .as("createAt 由 Service 显式写（§3.4 末）——⚠️ 此处只断言「已写」，"
                        + "因为 @PrePersist 兜底会给出同样的可观测结果，"
                        + "「是否显式赋值」属代码评审项而非可观测差异")
                .isNotNull();

        // BR-13：提交意向【不等于】冻结商品
        assertThat(fixtures.goodsRow(goodsId).get("status"))
                .as("提交意向绝不能改动商品状态（BR-13）")
                .isEqualTo("on_sale");
        assertThat(fixtures.goodsRow(goodsId).get("freeze_by")).isNull();
    }

    @Test
    @DisplayName("AC-18：队列达 1000 条上限时提交被拒、不新增记录（P1）")
    void submitIsRejectedWhenQueueIsFull() {
        String goodsId = fixtures.insertGoods("on_sale", null, "GDS-满队列商品", "10.00");
        // 造满 1000 条【非终态】：计数口径 = 非终态意向数（NFR-07 ①）
        assertThat(fixtures.insertIntentions(goodsId, QUEUE_MAX_SIZE, "queued"))
                .isEqualTo(QUEUE_MAX_SIZE);

        BuyerResult<SubmitResultData> result = buyerIntentionService
                .submitIntention("张三", "13800000000", freshClientKey());

        assertThat(result.rejectedPrompt()).isEqualTo(BuyerPrompt.QUEUE_FULL);
        assertThat(fixtures.countIntentions())
                .as("必须仍是 1000 条——第 1001 条不得落库")
                .isEqualTo(QUEUE_MAX_SIZE);
    }

    @Test
    @DisplayName("NFR-07 计数口径是【非终态】：终态意向不占队列容量")
    void queueCapacityCountsOnlyNonTerminalIntentions() {
        String goodsId = fixtures.insertGoods("on_sale", null, "GDS-终态不占位商品", "10.00");
        // 1000 条【终态】+ 0 条非终态 → 队列并未满，应可提交
        fixtures.insertIntentions(goodsId, QUEUE_MAX_SIZE, "succeeded");

        BuyerResult<SubmitResultData> result = buyerIntentionService
                .submitIntention("张三", "13800000000", freshClientKey());

        assertThat(result.rejectedPrompt())
                .as("1000 条终态意向不构成「队列已满」")
                .isNull();
    }

    // =========================================================================
    // NFR-17 限流（端到端，走 Service）
    // =========================================================================

    @Test
    @DisplayName("NFR-17：同一 IP 第 11 次提交被拒（限流真的在 Service 里生效）")
    void submitIsRateLimitedPerClientKey() {
        fixtures.insertGoods("on_sale", null, "GDS-限流商品", "10.00");
        String clientKey = freshClientKey();

        for (int i = 1; i <= 10; i++) {
            BuyerResult<SubmitResultData> allowed = buyerIntentionService
                    .submitIntention("张三", "13800000000", clientKey);
            assertThat(allowed.rejectedPrompt()).as("第 %d 次应放行", i).isNull();
        }

        BuyerResult<SubmitResultData> rejected = buyerIntentionService
                .submitIntention("张三", "13800000000", clientKey);
        assertThat(rejected.rejectedPrompt()).isEqualTo(BuyerPrompt.TOO_MANY_REQUESTS);
        assertThat(fixtures.countIntentions())
                .as("被限流的提交不得落库")
                .isEqualTo(10);
    }

    @Test
    @DisplayName("⚠️ 限流先于加锁：被限流的提交不应消耗队列序号")
    void rateLimitedSubmitDoesNotConsumeQueueOrder() {
        String goodsId = fixtures.insertGoods("on_sale", null, "GDS-限流不耗序号", "10.00");
        String clientKey = freshClientKey();
        for (int i = 0; i < 10; i++) {
            buyerIntentionService.submitIntention("张三", "13800000000", clientKey);
        }

        buyerIntentionService.submitIntention("张三", "13800000000", clientKey);

        Integer currentValue = jdbcTemplate.queryForObject(
                "select current_value from simpleshop_queue_sequence where id = 1", Integer.class);
        assertThat(currentValue)
                .as("10 次成功提交占用 1~10，被拒的那次不得把序号推到 11")
                .isEqualTo(10);
        assertThat(fixtures.countIntentions()).isEqualTo(10);
    }

    // =========================================================================
    // FR-16 修改姓名／电话
    // =========================================================================

    @Test
    @DisplayName("FR-16：改信息【不影响位次、不改原始提交时间】（BR-20、M10-10）")
    void updateContactKeepsQueueOrderAndCreateAt() {
        String goodsId = fixtures.insertGoods("on_sale", null, "GDS-改信息商品", "10.00");
        String intentionId = fixtures.insertIntention(goodsId, 1, "queued", "UPD000000001");
        Object createAtBefore = fixtures.intentionRow(intentionId).get("create_at");

        BuyerResult<Void> result = buyerIntentionService
                .updateIntentionContact("upd000000001", "新姓名", "13900000000");

        assertThat(result.rejectedPrompt()).isNull();
        var row = fixtures.intentionRow(intentionId);
        assertThat(row.get("name")).isEqualTo("新姓名");
        assertThat(row.get("tel")).isEqualTo("13900000000");
        assertThat(row.get("queue_order")).as("位次不变").isEqualTo(1);
        assertThat(row.get("create_at")).as("原始提交时间不变（BR-20）").isEqualTo(createAtBefore);
        assertThat(row.get("status")).as("状态不变").isEqualTo("queued");
        assertThat(row.get("token")).as("口令码不变").isEqualTo("UPD000000001");
    }

    @Test
    @DisplayName("FR-16：两项可独立修改——传 null 表示该项不改")
    void updateContactSupportsPartialUpdate() {
        String goodsId = fixtures.insertGoods("on_sale", null, "GDS-部分改商品", "10.00");
        String intentionId = fixtures.insertIntention(goodsId, 1, "queued", "PART00000001");

        // 只改姓名
        assertThat(buyerIntentionService.updateIntentionContact("PART00000001", "只改姓名", null)
                .rejectedPrompt()).isNull();
        assertThat(fixtures.intentionRow(intentionId).get("name")).isEqualTo("只改姓名");
        assertThat(fixtures.intentionRow(intentionId).get("tel")).isEqualTo("13800000000");

        // 只改电话
        assertThat(buyerIntentionService.updateIntentionContact("PART00000001", null, "13911112222")
                .rejectedPrompt()).isNull();
        assertThat(fixtures.intentionRow(intentionId).get("name")).isEqualTo("只改姓名");
        assertThat(fixtures.intentionRow(intentionId).get("tel")).isEqualTo("13911112222");
    }

    @Test
    @DisplayName("FR-16：两项都传 null 是【无操作成功】，不是错误（「不改」本身合法）")
    void updateContactWithBothNullIsNoOpSuccess() {
        String goodsId = fixtures.insertGoods("on_sale", null, "GDS-无操作商品", "10.00");
        String intentionId = fixtures.insertIntention(goodsId, 1, "queued", "NOOP00000001");

        assertThat(buyerIntentionService.updateIntentionContact("NOOP00000001", null, null).rejectedPrompt())
                .isNull();
        assertThat(fixtures.intentionRow(intentionId).get("name")).isEqualTo("GDS-买家");
    }

    @Test
    @DisplayName("FR-16：⚠️ null 与空串含义不同——空串是「用户清空了该项」→ 校验失败")
    void updateContactTreatsEmptyStringAsValidationFailure() {
        String goodsId = fixtures.insertGoods("on_sale", null, "GDS-空串商品", "10.00");
        String intentionId = fixtures.insertIntention(goodsId, 1, "queued", "EMPT00000001");

        assertThat(buyerIntentionService.updateIntentionContact("EMPT00000001", "", null).rejectedPrompt())
                .isEqualTo(BuyerPrompt.NAME_TEL_REQUIRED);
        assertThat(buyerIntentionService.updateIntentionContact("EMPT00000001", null, "   ").rejectedPrompt())
                .isEqualTo(BuyerPrompt.NAME_TEL_REQUIRED);
        assertThat(fixtures.intentionRow(intentionId).get("name"))
                .as("被拒时不得改动任何字段")
                .isEqualTo("GDS-买家");
    }

    @Test
    @DisplayName("FR-16：终态意向不接受修改（BR-31）")
    void updateContactRejectsTerminalIntention() {
        String goodsId = fixtures.insertGoods("on_sale", null, "GDS-终态改信息", "10.00");
        fixtures.insertIntention(goodsId, 1, "revoked", "TERM00000001");

        assertThat(buyerIntentionService.updateIntentionContact("TERM00000001", "新姓名", null).rejectedPrompt())
                .isEqualTo(BuyerPrompt.PASSCODE_EXPIRED);
    }

    // =========================================================================
    // FR-17 撤销意向
    // =========================================================================

    @Test
    @DisplayName("IS-03：撤销 queued 意向 → 状态 revoked、failType revoked（自动型）")
    void revokeSetsStatusAndFailType() {
        String goodsId = fixtures.insertGoods("on_sale", null, "GDS-撤销商品", "10.00");
        String intentionId = fixtures.insertIntention(goodsId, 1, "queued", "REVK00000001");

        assertThat(buyerIntentionService.revokeIntention("REVK00000001").rejectedPrompt()).isNull();

        var row = fixtures.intentionRow(intentionId);
        assertThat(row.get("status")).isEqualTo("revoked");
        assertThat(row.get("fail_type")).isEqualTo("revoked");
        assertThat(row.get("fail_reason")).isNull();
        // 撤销后口令码失效（终态）
        assertThat(buyerIntentionService.queryByToken("REVK00000001").prompt())
                .isEqualTo(BuyerPrompt.PASSCODE_EXPIRED);
    }

    @Test
    @DisplayName("BR-19：trading 意向不可撤销（B11-08／M10-14）")
    void revokeRejectsTradingIntention() {
        String goodsId = fixtures.insertGoods("frozen", "trade", "GDS-交易中商品", "10.00");
        String intentionId = fixtures.insertIntention(goodsId, 1, "trading", "TRDG00000001");

        assertThat(buyerIntentionService.revokeIntention("TRDG00000001").rejectedPrompt())
                .isEqualTo(BuyerPrompt.REVOKE_NOT_ALLOWED);
        assertThat(fixtures.intentionRow(intentionId).get("status"))
                .as("被拒时状态必须保持 trading")
                .isEqualTo("trading");
    }

    @Test
    @DisplayName("撤销终态意向 → 口令码已失效（而不是「撤销成功」）")
    void revokeRejectsTerminalIntention() {
        String goodsId = fixtures.insertGoods("on_sale", null, "GDS-终态撤销", "10.00");
        fixtures.insertIntention(goodsId, 1, "succeeded", "TRMV00000001");

        assertThat(buyerIntentionService.revokeIntention("TRMV00000001").rejectedPrompt())
                .isEqualTo(BuyerPrompt.PASSCODE_EXPIRED);
    }

    @Test
    @DisplayName("撤销/改信息：口令码不存在 → 错误口令（B11-04）")
    void tokenOperationsRejectUnknownToken() {
        assertThat(buyerIntentionService.revokeIntention("NOSUCH000001").rejectedPrompt())
                .isEqualTo(BuyerPrompt.PASSCODE_NOT_FOUND);
        assertThat(buyerIntentionService.updateIntentionContact("NOSUCH000001", "张三", null).rejectedPrompt())
                .isEqualTo(BuyerPrompt.PASSCODE_NOT_FOUND);
        assertThat(buyerIntentionService.revokeIntention(null).rejectedPrompt())
                .isEqualTo(BuyerPrompt.PASSCODE_NOT_FOUND);
    }

    // =========================================================================
    // FR-12 / FR-19 浏览当前商品
    // =========================================================================

    @Test
    @DisplayName("FR-12：无商品时返回 null（空态不是错误分支，由调用方渲染 M10-02）")
    void browseCurrentGoodsReturnsNullWhenEmpty() {
        assertThat(buyerIntentionService.browseCurrentGoods()).isNull();
    }

    @Test
    @DisplayName("FR-12/FR-18：冻结的商品【照样返回】——页面要展示卡片 + 已冻结标签 + M10-01")
    void browseCurrentGoodsReturnsFrozenGoodsToo() {
        // 若这里把冻结商品过滤掉，买家会看到「暂无在售商品」，
        // 与「商品交易中，暂停接收新意向」的含义完全不同（第 10 章 P10-01 的状态表）
        fixtures.insertGoods("frozen", "trade", "GDS-冻结中商品", "20.00");

        GoodsData goods = buyerIntentionService.browseCurrentGoods();

        assertThat(goods).isNotNull();
        assertThat(goods.status()).isEqualTo("frozen");
        assertThat(goods.freezeBy()).isEqualTo("trade");
        assertThat(goods.price()).isEqualTo("20.00");
    }

    // =========================================================================
    // NFR-12 边界：买家写操作不属于 8 类必列操作
    // =========================================================================

    @Test
    @DisplayName("NFR-12：买家提交/撤销【不】写操作日志（8 类必列操作全是卖家侧动作）")
    void buyerOperationsWriteNoAuditLog() {
        fixtures.insertGoods("on_sale", null, "GDS-无审计商品", "10.00");

        try (AuditLogCapture audit = AuditLogCapture.audit()) {
            SubmitResultData submitted = buyerIntentionService
                    .submitIntention("张三", "13800000000", freshClientKey()).orElseNull();
            assertThat(submitted).as("前置：提交应成功（否则后面的调用会 NPE，掩盖真正的问题）").isNotNull();
            buyerIntentionService.queryByToken(submitted.token());
            buyerIntentionService.updateIntentionContact(submitted.token(), "李四", null);
            buyerIntentionService.revokeIntention(submitted.token());

            assertThat(audit.messages())
                    .as("NFR-12 的 8 类操作全是卖家的状态变更；把买家动作也记进去会让"
                            + "「8 类各有记录」的验收口径变成含糊的「大约 8 类」")
                    .isEmpty();
        }
    }

    // =========================================================================
    // 辅助
    // =========================================================================

    /** 取一个全新的限流 key，避免用例之间通过单例计数器互相影响（见类注释）。 */
    private static String freshClientKey() {
        return "test-client-" + CLIENT_KEYS.incrementAndGet();
    }
}
