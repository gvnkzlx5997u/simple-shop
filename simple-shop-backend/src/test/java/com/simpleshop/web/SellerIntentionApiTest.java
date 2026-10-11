package com.simpleshop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.simpleshop.service.BuyerIntentionService;
import com.simpleshop.service.SellerIntentionService;
import com.simpleshop.service.dto.BuyerPrompt;
import com.simpleshop.service.dto.BuyerResult;
import com.simpleshop.service.dto.SubmitResultData;
import com.simpleshop.session.SessionStore;
import com.simpleshop.testing.AuditLogCapture;
import com.simpleshop.testing.CsvValues;
import com.simpleshop.testing.ShopFixtures;

/**
 * 意向与交易接口的契约与状态机验证（{@code I11-10} ~ {@code I11-14}）——S6 的验收凭据。
 *
 * <p>覆盖方案 §8.3 的「状态机 {@code PS-01}~{@code PS-04}、{@code IS-02}~{@code IS-07}」条目、
 * 归档/流水/并发三张专项表（#12~#19），以及 §12 的 {@code AC-16}／{@code AC-22}／{@code INV-05}／
 * {@code INV-06}／{@code INV-07}／{@code INV-08}。
 *
 * <h2>三张 CSV 用例表驱动本类</h2>
 * <table border="1">
 *   <caption>用例表与覆盖面</caption>
 *   <tr><th>用例表</th><th>覆盖</th></tr>
 *   <tr><td>{@code intention-trade-cases.csv}</td>
 *       <td>{@code I11-11}：队首成功、非队首（{@code BR-12}）、无队首、商品非在售、
 *           无商品、以及「在售却有 {@code trading} 意向」时 {@code 30004} 的防御性拦截</td></tr>
 *   <tr><td>{@code intention-success-cases.csv}</td>
 *       <td>{@code I11-12}：成功、商品在售／手动冻结（Q-1 的 {@code 20004}）、目标非 {@code trading}</td></tr>
 *   <tr><td>{@code intention-failure-cases.csv}</td>
 *       <td>{@code I11-13}：作废／重排队、{@code disposal} 的 5 种非法取值（{@code 30006}）、
 *           {@code fail_reason} 的长度与字符边界（{@code 30007}）、以及「字段校验在取锁之前」的顺序</td></tr>
 * </table>
 *
 * <h2>⚠️ 用例表的 {@code goodsSetup} 为什么要走真实流程造 {@code frozen+trade}</h2>
 * <p>「交易冻结」这个状态只能由 {@code enterTrade} 产生，且它会<b>同时</b>写入
 * {@code goods.trade_start}——而 {@code trade_start} 是追加流水时 {@code NOT NULL} 的必填列。
 * 若用 JDBC 直接插一个 {@code frozen+trade} 的商品（{@code trade_start} 为 {@code NULL}），
 * 后续「标记交易结果」会以一条与业务无关的 {@code NOT NULL} 约束错误失败。
 * 因此本类统一<b>通过 {@code I11-11} 把商品推进该状态</b>，而不是伪造它。
 * （需要 {@code frozen+manual} 的用例仍直接插库——那是「卖家手动冻结」的产物，
 * 且这些用例都在触碰流水之前就被 {@code 20004} 拒绝。）
 *
 * <h2>⚠️ 每个用例前后清空商品域</h2>
 * <p>与 S4 同理：商品表至多 1 行（{@code INV-01}），且本类的全部接口都以「当前商品」定位。
 * 清理逻辑收在 {@link ShopFixtures}（S5 起共用），它同时把序号表复位、
 * 并在造数时保证 {@code current_value} ≥ 已造出的最大 {@code queue_order}
 * ——否则「重新排队」会分到已被占用的序号（见该类的说明）。
 */
@SpringBootTest
@ActiveProfiles("test")
class SellerIntentionApiTest {

    /** 契约要求的时间形状（方案 §8.3 断言 #3）。 */
    private static final String CONTRACT_TIME_SHAPE = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z";

    /** 测试用账号（V2 种子）。 */
    private static final String ACCOUNT = "seller";

    /** 一个必然不存在的意向 ID（用于「不存在」类分支）。 */
    private static final String NO_SUCH_INTENTION = "INOSUCH0000001";

    private static final AtomicInteger CLIENT_KEY_SEQ = new AtomicInteger(1);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private SellerIntentionService sellerIntentionService;

    @Autowired
    private BuyerIntentionService buyerIntentionService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private ShopFixtures fixtures;

    private String token;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        fixtures = new ShopFixtures(jdbcTemplate);
        fixtures.cleanGoodsState();
        fixtures.resetSequence();
        sessionStore.removeAllFor(ACCOUNT);
        token = sessionStore.create(ACCOUNT).token();
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanGoodsState();
        sessionStore.removeAllFor(ACCOUNT);
    }

    // =========================================================================
    // I11-10 查询意向名单
    // =========================================================================

    @Test
    @DisplayName("I11-10 无当前商品时：data 为 null、code 为 0，且 data 键【必须存在】（已定 Q-2）")
    void listIntentionsWithoutGoodsReturnsNullData() throws Exception {
        mockMvc.perform(authorizedGet(SellerIntentionController.INTENTIONS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(nullValue()))
                // 断言「键存在」而不是「值为假」：一旦有人加了 @JsonInclude(NON_NULL)，
                // 上面那条 jsonPath 会因路径不存在而失败，这一条则把契约要求说得更直白。
                .andExpect(content().string(containsString("\"data\":null")));
    }

    @Test
    @DisplayName("I11-10 名单：按 queue_order 升序、trading 的 rank 为 null、queue_count 为非终态数")
    void listIntentionsOrderRankAndQueueCount() throws Exception {
        String goodsId = fixtures.insertGoods("on_sale", null, "用例商品", "12.50");
        String first = fixtures.insertIntention(goodsId, "queued");
        String second = fixtures.insertIntention(goodsId, "queued");
        String third = fixtures.insertIntention(goodsId, "queued");
        // 走真实流程把队首推进 trading，于是队列里同时存在 trading 与 queued
        enterTrade(first);

        String body = mockMvc.perform(authorizedGet(SellerIntentionController.INTENTIONS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.page_size").value(10))
                .andExpect(jsonPath("$.data.queue_count").value(3))
                // 顺序：queue_order 升序
                .andExpect(jsonPath("$.data.items[*].id").value(contains(first, second, third)))
                // trading 不占位次 → rank 为 null；其余按 queued 排名
                .andExpect(jsonPath("$.data.items[0].rank").value(nullValue()))
                .andExpect(jsonPath("$.data.items[1].rank").value(1))
                .andExpect(jsonPath("$.data.items[2].rank").value(2))
                .andExpect(jsonPath("$.data.items[0].status").value("trading"))
                .andExpect(jsonPath("$.data.items[1].status").value("queued"))
                // 时间形状（含末尾 Z）
                .andExpect(jsonPath("$.data.items[0].create_at").value(
                        org.hamcrest.Matchers.matchesPattern(CONTRACT_TIME_SHAPE)))
                .andReturn().getResponse().getContentAsString();

        // ⚠️ 安全项：响应体里【绝不】出现任何口令码（11-D、BR-30、NFR-16）
        for (String intentionId : List.of(first, second, third)) {
            assertThat(body).doesNotContain(fixtures.tokenOf(intentionId));
        }
        // 也不含 trade_start（澄清 Q22：只记录、不展示）
        assertThat(body).doesNotContain("trade_start");
    }

    @Test
    @DisplayName("I11-10 分页：total 是总数、items 是本页、page/page_size 回显请求值")
    void listIntentionsPaging() throws Exception {
        String goodsId = fixtures.insertGoods("on_sale", null, "用例商品", "12.50");
        String first = fixtures.insertIntention(goodsId, "queued");
        String second = fixtures.insertIntention(goodsId, "queued");
        String third = fixtures.insertIntention(goodsId, "queued");

        mockMvc.perform(authorizedGet(SellerIntentionController.INTENTIONS_PATH + "?page=2&page_size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.page_size").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(third));

        mockMvc.perform(authorizedGet(SellerIntentionController.INTENTIONS_PATH + "?page_size=2"))
                .andExpect(jsonPath("$.data.items[*].id").value(contains(first, second)));
    }

    @Test
    @DisplayName("I11-10 名单含终态行：status 与 fail_type 均以【小写代码】下发（9-Q）")
    void listIntentionsIncludesTerminalRows() throws Exception {
        String goodsId = fixtures.insertGoods("on_sale", null, "用例商品", "12.50");
        String head = fixtures.insertIntention(goodsId, "queued");
        fixtures.insertIntention(goodsId, "queued");
        enterTrade(head);
        // 走真实流程把这次交易标记失败（作废），于是库里留下一条 failed + voided 的终态意向
        markFailure(head, "voided", "买家联系不上");

        mockMvc.perform(authorizedGet(SellerIntentionController.INTENTIONS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("failed"))
                .andExpect(jsonPath("$.data.items[0].fail_type").value("voided"))
                .andExpect(jsonPath("$.data.items[0].fail_reason").value("买家联系不上"))
                // queue_count 只数非终态：一条 failed 已退出队列
                .andExpect(jsonPath("$.data.queue_count").value(1));
    }

    @ParameterizedTest(name = "I11-10 分页参数越界：{0} → 400 + 50002")
    @CsvSource({"page=0", "page=-1", "page_size=0", "page_size=101"})
    @DisplayName("I11-10 分页参数越界 → HTTP 400 + 50002（⚠️不是 500）")
    void listIntentionsRejectsBadPaging(String query) throws Exception {
        fixtures.insertGoods("on_sale", null, "用例商品", "12.50");
        mockMvc.perform(authorizedGet(SellerIntentionController.INTENTIONS_PATH + "?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50002));
    }

    // =========================================================================
    // I11-11 进入交易
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(resources = "/csv/intention-trade-cases.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("I11-11 进入交易：状态迁移与拒绝分支（用例表驱动）")
    void enterTradeCases(String caseName, String goodsSetup, String targetKind, String extraTrading,
                         int expectedHttp, int expectedCode, String expectedGoodsStatus) throws Exception {
        Ctx ctx = prepare(goodsSetup, targetKind, "YES".equals(extraTrading));

        mockMvc.perform(authorizedPost(SellerIntentionController.TRADE_PATH, intentionIdBody(ctx.targetId())))
                .andExpect(status().is(expectedHttp))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.data").value(nullValue()));

        if (expectedGoodsStatus != null) {
            assertThat(fixtures.goodsRow(ctx.goodsId()).get("status"))
                    .as("%s：商品状态不应被改动", caseName)
                    .isEqualTo(expectedGoodsStatus);
        }
    }

    @Test
    @DisplayName("I11-11 成功：商品 frozen+trade、写 trade_start、目标 trading、其余 queued 不变、且不产生流水")
    void enterTradeSideEffects() throws Exception {
        String goodsId = fixtures.insertGoods("on_sale", null, "用例商品", "12.50");
        String head = fixtures.insertIntention(goodsId, "queued");
        String second = fixtures.insertIntention(goodsId, "queued");

        enterTrade(head);

        Map<String, Object> goods = fixtures.goodsRow(goodsId);
        assertThat(goods.get("status")).isEqualTo("frozen");
        assertThat(goods.get("freeze_by")).isEqualTo("trade");
        assertThat(goods.get("trade_start")).as("进入交易时写 trade_start").isNotNull();
        assertThat(goods.get("trade_end")).as("当前表 trade_end 恒 NULL").isNull();
        assertThat(goods.get("result")).as("当前表 result 恒 NULL（DEC-DB-11）").isNull();

        assertThat(fixtures.intentionRow(head).get("status")).isEqualTo("trading");
        assertThat(fixtures.intentionRow(second).get("status"))
                .as("BR-01：其余 queued 意向保持不变").isEqualTo("queued");

        assertThat(flowCount(head)).as("enterTrade 不追加流水（trade_end 尚无值）").isZero();
    }

    // =========================================================================
    // I11-12 标记交易成功
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(resources = "/csv/intention-success-cases.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("I11-12 标记成功：成功与拒绝分支（用例表驱动）")
    void markSuccessCases(String caseName, String goodsSetup, String targetKind,
                          int expectedHttp, int expectedCode, String expectedTargetStatus) throws Exception {
        Ctx ctx = prepare(goodsSetup, targetKind, false);

        mockMvc.perform(authorizedPost(SellerIntentionController.SUCCESS_PATH, intentionIdBody(ctx.targetId())))
                .andExpect(status().is(expectedHttp))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.data").value(nullValue()));

        if (!"NONE".equals(goodsSetup) && !"missing".equals(targetKind)) {
            assertThat(intentionRowAnywhere(ctx.targetId()).get("status"))
                    .as("%s：被拒时目标意向状态不应被改动", caseName)
                    .isEqualTo(expectedTargetStatus);
        }
    }

    @Test
    @DisplayName("I11-12 成功：其余 queued → failed+sold_out、本次 → succeeded、追加 1 条流水、商品归档")
    void markSuccessHappyPath() throws Exception {
        String goodsId = fixtures.insertGoods("on_sale", null, "用例商品", "12.50");
        String head = fixtures.insertIntention(goodsId, "queued");
        String second = fixtures.insertIntention(goodsId, "queued");
        String third = fixtures.insertIntention(goodsId, "queued");
        enterTrade(head);
        LocalDateTime tradeStart = (LocalDateTime) fixtures.goodsRow(goodsId).get("trade_start");

        mockMvc.perform(authorizedPost(SellerIntentionController.SUCCESS_PATH, intentionIdBody(head)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // ① 其余 queued → failed + sold_out（BR-03 的顺序要求「先他人失败」）
        for (String other : List.of(second, third)) {
            Map<String, Object> row = fixtures.intentionRowInHistory(other);
            assertThat(row.get("status")).isEqualTo("failed");
            assertThat(row.get("fail_type")).isEqualTo("sold_out");
            assertThat(row.get("create_at")).as("历史意向继承原始提交时间").isNotNull();
        }
        // ② 本次 → succeeded，且失败语义已清空
        Map<String, Object> succeeded = fixtures.intentionRowInHistory(head);
        assertThat(succeeded.get("status")).isEqualTo("succeeded");
        assertThat(succeeded.get("fail_type")).isNull();
        assertThat(succeeded.get("fail_reason")).isNull();

        // ③ 流水恰好 1 条，且只属于本次成交（IS-04 的连带失败不追加流水）
        assertThat(flowCount(head)).isEqualTo(1);
        Map<String, Object> flow = fixtures.tradeFlow(head);
        assertThat(flow.get("result")).isEqualTo("sold");
        assertThat(flow.get("fail_type")).isNull();
        assertThat(flow.get("trade_start")).isEqualTo(tradeStart);
        assertThat(flow.get("trade_end")).isNotNull();
        assertThat(flowCount(second)).isZero();
        assertThat(flowCount(third)).isZero();

        // ④ 归档：当前表清空、历史商品 1 行且 result = sold
        assertThat(fixtures.countGoods()).isZero();
        assertThat(fixtures.countIntentions()).isZero();
        Map<String, Object> history = fixtures.goodsHistoryRow(goodsId);
        assertThat(history.get("status")).isEqualTo("off_sale");
        assertThat(history.get("freeze_by")).isNull();
        assertThat(history.get("result")).isEqualTo("sold");
        assertThat(history.get("trade_end")).isNotNull();
        assertThat(history.get("id")).isEqualTo(goodsId);
        // INV-05／INV-06：历史表里不得再有非终态意向，且 succeeded 恰为 1 条
        assertThat(fixtures.count("select count(*) from simpleshop_intentions_history "
                + "where status in ('queued','trading')")).isZero();
        assertThat(fixtures.count("select count(*) from simpleshop_intentions_history "
                + "where fk_goods_history_id = ? and status = 'succeeded'", goodsId)).isEqualTo(1);
    }

    // =========================================================================
    // I11-13 标记交易失败
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(resources = "/csv/intention-failure-cases.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("I11-13 标记失败：裁决、disposal/fail_reason 校验与字段顺序（用例表驱动）")
    void markFailureCases(String caseName, String goodsSetup, String targetKind,
                          String disposal, String failReason,
                          int expectedHttp, int expectedCode,
                          String expectedStatus, String expectedFailType) throws Exception {
        Ctx ctx = prepare(goodsSetup, targetKind, false);

        mockMvc.perform(authorizedPost(SellerIntentionController.FAILURE_PATH,
                        failureBody(ctx.targetId(), disposal, failReason)))
                .andExpect(status().is(expectedHttp))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.data").value(nullValue()));

        if ("NONE".equals(goodsSetup)) {
            // 无商品用例：连意向行都不存在，后面的逐列断言无从谈起
            assertThat(ctx.goodsId()).as("用例表要求无商品").isNull();
            assertThat(expectedStatus).as("无商品用例不应期待意向行状态").isNull();
            return;
        }

        Map<String, Object> row = intentionRowAnywhere(ctx.targetId());
        assertThat(row.get("status")).as("%s：意向状态", caseName).isEqualTo(expectedStatus);
        assertThat(row.get("fail_type")).as("%s：fail_type", caseName).isEqualTo(expectedFailType);

        if (expectedCode == 0) {
            // 成功路径：归一化后的备注必须原样落库（含换行、恰好 300 字符）
            assertThat(row.get("fail_reason"))
                    .as("%s：失败备注", caseName)
                    .isEqualTo(CsvValues.expand(failReason));
            assertThat(flowCount(ctx.targetId())).as("%s：无论作废还是重排队都记 1 条流水", caseName)
                    .isEqualTo(1);
        } else {
            // 被拒路径：一条流水都不应追加（拒绝分支全部在写入之前）
            assertThat(flowCount(ctx.targetId())).as("%s：被拒时不得追加流水", caseName).isZero();
        }
    }

    @Test
    @DisplayName("I11-13 作废：意向 failed+voided、商品恢复在售、流水记 result=failed")
    void markFailureVoided() throws Exception {
        String goodsId = fixtures.insertGoods("on_sale", null, "用例商品", "12.50");
        String head = fixtures.insertIntention(goodsId, "queued");
        String second = fixtures.insertIntention(goodsId, "queued");
        enterTrade(head);
        LocalDateTime tradeStart = (LocalDateTime) fixtures.goodsRow(goodsId).get("trade_start");

        markFailure(head, "voided", "买家临时变卦");

        assertThat(fixtures.intentionRow(head).get("status")).isEqualTo("failed");
        assertThat(fixtures.intentionRow(head).get("fail_type")).isEqualTo("voided");
        assertThat(fixtures.intentionRow(head).get("fail_reason")).isEqualTo("买家临时变卦");
        assertThat(fixtures.intentionRow(second).get("status"))
                .as("BR-04：其余 queued 状态不变（位次是计数派生，无需改数据）").isEqualTo("queued");

        Map<String, Object> goods = fixtures.goodsRow(goodsId);
        assertThat(goods.get("status")).isEqualTo("on_sale");
        assertThat(goods.get("freeze_by")).isNull();
        assertThat(goods.get("trade_start"))
                .as("tradeStart 保留不清（下次进入交易会覆盖它）").isNotNull();

        Map<String, Object> flow = fixtures.tradeFlow(head);
        assertThat(flow.get("result")).isEqualTo("failed");
        assertThat(flow.get("fail_type")).isEqualTo("voided");
        assertThat(flow.get("fail_reason")).isEqualTo("买家临时变卦");
        assertThat(flow.get("trade_start")).isEqualTo(tradeStart);
    }

    @Test
    @DisplayName("I11-13 重新排队：序号刷新至队尾、create_at 与 token 不变、口令码仍有效（AC-16、BR-27）")
    void markFailureRequeued() throws Exception {
        String goodsId = fixtures.insertGoods("on_sale", null, "用例商品", "12.50");
        String head = fixtures.insertIntention(goodsId, "queued");
        String second = fixtures.insertIntention(goodsId, "queued");
        String third = fixtures.insertIntention(goodsId, "queued");
        enterTrade(head);

        Map<String, Object> before = fixtures.intentionRow(head);
        String tokenBefore = fixtures.tokenOf(head);
        LocalDateTime createAtBefore = (LocalDateTime) before.get("create_at");

        markFailure(head, "requeued", "买家要求改期");

        Map<String, Object> after = fixtures.intentionRow(head);
        assertThat(after.get("status")).isEqualTo("queued");
        assertThat(after.get("fail_type"))
                .as("DEC-DB-10 ①：重排队后 fail_type 保留 requeued（最近一次处置的快照）")
                .isEqualTo("requeued");
        assertThat(after.get("fail_reason")).isEqualTo("买家要求改期");
        assertThat((Integer) after.get("queue_order"))
                .as("BR-20：序号刷新至当前最大值")
                .isGreaterThan((Integer) fixtures.intentionRow(third).get("queue_order"));
        assertThat(after.get("create_at"))
                .as("BR-20／DEC-15：原始提交时间不变").isEqualTo(createAtBefore);
        assertThat(fixtures.tokenOf(head))
                .as("IS-07：口令码不变 → 仍然有效").isEqualTo(tokenBefore);
        assertThat(after.get("id")).as("DEC-04：复用原记录，不新建").isEqualTo(head);

        // 口令码仍有效：I11-14 应能取到同一个码
        mockMvc.perform(authorizedGet(passcodeQuery(head)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value(tokenBefore));

        // 位次：重排队后该意向排到最后（AC-16 ④ 原队列其余买家各前移 1）
        mockMvc.perform(authorizedGet(SellerIntentionController.INTENTIONS_PATH))
                .andExpect(jsonPath("$.data.items[0].id").value(second))
                .andExpect(jsonPath("$.data.items[0].rank").value(1))
                .andExpect(jsonPath("$.data.items[1].id").value(third))
                .andExpect(jsonPath("$.data.items[1].rank").value(2))
                .andExpect(jsonPath("$.data.items[2].id").value(head))
                .andExpect(jsonPath("$.data.items[2].rank").value(3));
    }

    // =========================================================================
    // I11-14 查看口令码
    // =========================================================================

    @Test
    @DisplayName("I11-14 非终态意向：返回口令码原文，且记 1 条【不含码值】的审计日志（NFR-12、12-H）")
    void getPasscodeAndAuditLog() throws Exception {
        String goodsId = fixtures.insertGoods("on_sale", null, "用例商品", "12.50");
        String intentionId = fixtures.insertIntention(goodsId, "queued");
        String stored = fixtures.tokenOf(intentionId);
        assertThat(stored).matches("^[A-Z0-9]{12}$");

        try (AuditLogCapture audit = AuditLogCapture.audit()) {
            mockMvc.perform(authorizedGet(passcodeQuery(intentionId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.token").value(stored));

            assertThat(audit.count()).as("查看口令码须记日志（NFR-12 第 7 类）").isEqualTo(1);
            assertThat(audit.joined()).contains("VIEW_PASSCODE").contains(intentionId);
            // ⚠️ NFR-12／12-H：日志文本中不得出现该口令码的任何子串
            assertThat(audit.joined())
                    .as("审计日志不得记录口令码明文")
                    .doesNotContain(stored)
                    .doesNotContain(stored.substring(0, 6));
        }
    }

    @ParameterizedTest(name = "I11-14 终态（{0}）→ 40002 且响应体不含口令码")
    @CsvSource({"succeeded", "failed", "revoked"})
    @DisplayName("I11-14 终态意向 → 40002，且响应体【不】含 token（NFR-16）")
    void getPasscodeOnTerminalStatus(String status) throws Exception {
        String goodsId = fixtures.insertGoods("on_sale", null, "用例商品", "12.50");
        String intentionId = fixtures.insertIntention(goodsId, status);
        String stored = fixtures.tokenOf(intentionId);

        String body = mockMvc.perform(authorizedGet(passcodeQuery(intentionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(stored);
    }

    @Test
    @DisplayName("I11-14 非终态含 trading：进入交易后口令码仍可查看（§3.3.7 的 isTokenValid = !terminal）")
    void getPasscodeForTradingIntention() throws Exception {
        String goodsId = fixtures.insertGoods("on_sale", null, "用例商品", "12.50");
        String head = fixtures.insertIntention(goodsId, "queued");
        String stored = fixtures.tokenOf(head);

        enterTrade(head);

        // ⚠️ trading 是【非终态】——口令码对它必须依然有效。
        //    §3.3.7 把有效性定义成 isTokenValid() = !isTerminal()，即 queued 与 trading 都算；
        //    这里钉住的就是「有人误以为只有 queued 有效」这个假设不成立。
        mockMvc.perform(authorizedGet(passcodeQuery(head)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value(stored));

        // 且名单里它已经是 trading（说明上面的码确实对应「交易中」的意向，不是排队中）
        mockMvc.perform(authorizedGet(SellerIntentionController.INTENTIONS_PATH))
                .andExpect(jsonPath("$.data.items[0].status").value("trading"))
                .andExpect(jsonPath("$.data.items[0].rank").value(nullValue()));
    }

    @Test
    @DisplayName("I11-14 意向不存在 → 30008；缺 intention_id → 400 + 50002")
    void getPasscodeNotFoundAndMissingParam() throws Exception {
        fixtures.insertGoods("on_sale", null, "用例商品", "12.50");

        mockMvc.perform(authorizedGet(passcodeQuery(NO_SUCH_INTENTION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30008));

        mockMvc.perform(authorizedGet(SellerIntentionController.PASSCODE_PATH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50002));

        mockMvc.perform(authorizedGet(SellerIntentionController.PASSCODE_PATH + "?intention_id="))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50002));
    }

    // =========================================================================
    // 鉴权与路径契约
    // =========================================================================

    @Test
    @DisplayName("五个端点都要求 token：无 Authorization → 401 + 10002")
    void allEndpointsRequireToken() throws Exception {
        fixtures.insertGoods("on_sale", null, "用例商品", "12.50");

        List<MockHttpServletRequestBuilder> unauthenticated = List.of(
                get(SellerIntentionController.INTENTIONS_PATH),
                post(SellerIntentionController.TRADE_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(intentionIdBody(NO_SUCH_INTENTION)),
                post(SellerIntentionController.SUCCESS_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(intentionIdBody(NO_SUCH_INTENTION)),
                post(SellerIntentionController.FAILURE_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(failureBody(NO_SUCH_INTENTION, "voided", null)),
                get(passcodeQuery(NO_SUCH_INTENTION)));

        for (MockHttpServletRequestBuilder request : unauthenticated) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(10002));
        }
    }

    @Test
    @DisplayName("路径与方法：I11-10 在 /product 下、其余在 /intention 下（单数）；动词错配 → 405")
    void pathsAndMethods() throws Exception {
        fixtures.insertGoods("on_sale", null, "用例商品", "12.50");

        // I11-10 的路径确实是 /api/seller/product/intentions（契约如此，容易写成 /intention）
        assertThat(SellerIntentionController.INTENTIONS_PATH).isEqualTo("/api/seller/product/intentions");
        assertThat(SellerIntentionController.TRADE_PATH).isEqualTo("/api/seller/intention/trade");
        assertThat(SellerIntentionController.SUCCESS_PATH).isEqualTo("/api/seller/intention/success");
        assertThat(SellerIntentionController.FAILURE_PATH).isEqualTo("/api/seller/intention/failure");
        assertThat(SellerIntentionController.PASSCODE_PATH).isEqualTo("/api/seller/intention/passcode");

        // 动词错配：对 GET 端点用 POST、对 POST 端点用 GET → 405（契约未规定，保留 REST 惯例）
        mockMvc.perform(authorizedPost(SellerIntentionController.INTENTIONS_PATH, "{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(authorizedGet(SellerIntentionController.TRADE_PATH))
                .andExpect(status().isMethodNotAllowed());
    }

    // =========================================================================
    // 并发：加锁顺序（风险 R3）
    // =========================================================================

    @Test
    @DisplayName("并发：重新排队（先序号表→商品行）与买家提交（先序号表）不构成反向取锁，两者都成功且不超时")
    void concurrentRequeueAndBuyerSubmitDoNotDeadlock() throws Exception {
        String goodsId = fixtures.insertGoods("on_sale", null, "用例商品", "12.50");
        String head = fixtures.insertIntention(goodsId, "queued");
        fixtures.insertIntention(goodsId, "queued");
        enterTrade(head);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> requeue = pool.submit(() -> {
                start.await();
                tx.execute(status -> {
                    sellerIntentionService.markTradeFailure(head, "requeued", null);
                    return null;
                });
                return (Integer) fixtures.intentionRow(head).get("queue_order");
            });
            Future<SubmitResultData> buyer = pool.submit(() -> {
                start.await();
                // 买家提交要求商品在售，而它此刻正处于「交易冻结」——真实场景里买家是在
                // 卖家处理完之后才点的提交按钮。这里让它**重试到最后一次尝试成功**，
                // 从而真正与「重新排队」在序号表行锁上竞争，而不是简单地被 INV-03 挡掉。
                // ⚠️ 每次重试必须是一个【新事务】：MySQL 默认 REPEATABLE READ 下，
                //    同一个事务的读视图在首次读之后就固定了，重试会一直看到旧的 frozen。
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (System.nanoTime() < deadline) {
                    BuyerResult<SubmitResultData> result = tx.execute(status ->
                            buyerIntentionService.submitIntention("并发买家", "13800000000",
                                    "concurrent-" + CLIENT_KEY_SEQ.getAndIncrement()));
                    if (result instanceof BuyerResult.Ok<SubmitResultData> ok) {
                        return ok.data();
                    }
                    // 商品仍在交易冻结中（GOODS_FROZEN）→ 稍后再试；其它拒绝视为失败
                    if (result.rejectedPrompt() != BuyerPrompt.GOODS_FROZEN) {
                        throw new AssertionError("买家提交被非预期地拒绝：" + result.rejectedPrompt());
                    }
                    Thread.sleep(10);
                }
                throw new AssertionError("买家提交在 5 秒内始终未成功——疑似被行锁阻塞");
            });

            start.countDown();
            int requeuedOrder = requeue.get(10, TimeUnit.SECONDS);
            SubmitResultData submitted = buyer.get(10, TimeUnit.SECONDS);

            assertThat(fixtures.intentionRow(head).get("queue_order")).isEqualTo(requeuedOrder);
            Map<String, Object> submittedRow = fixtures.intentionRow(submitted.intentionId());
            assertThat(submittedRow.get("status")).isEqualTo("queued");
            assertThat(submittedRow.get("queue_order"))
                    .as("两条路径各自从序号表取得互不相同的序号")
                    .isNotEqualTo(requeuedOrder);
        } finally {
            pool.shutdownNow();
        }
        // 备注：本用例断言的是「不死锁、不超时、两条路径都完成」。
        // 死锁在本方法集下【结构上不可达】——全系统只有 markTradeFailure(requeued) 一处
        // 同时持有「序号表行 + 商品行」，而序号表行从不在商品行之后被获取，
        // 因此不存在环路。本用例的价值是：一旦有人把商品行锁提到序号表锁之前
        // （即改回错误的取锁顺序），它会以超时/失败的形式暴露出来。
    }

    // =========================================================================
    // 用例辅助：夹具与请求
    // =========================================================================

    /** 一次用例的现场：商品、队首、第二条意向、以及本次要操作的目标意向。 */
    private record Ctx(String goodsId, String headId, String secondId, String targetId) {
    }

    /**
     * 按用例表的两列（{@code goodsSetup}／{@code targetKind}）造出现场。
     *
     * <p>{@code goodsSetup}：
     * <ul>
     *   <li>{@code on_sale}：在售商品 + 2 条 {@code queued}；</li>
     *   <li>{@code manual}：{@code frozen+manual} 的商品 + 2 条 {@code queued}（手动冻结的产物，直接插库）；</li>
     *   <li>{@code trade}：先造在售商品，再<b>走 {@code I11-11}</b> 把商品推进 {@code frozen+trade}
     *       并把队首推进 {@code trading}（这样 {@code trade_start} 才是真实写入的）；</li>
     *   <li>{@code NONE}：没有商品。</li>
     * </ul>
     * 另有个别用例需要「在售却有 {@code trading} 意向」这种违反 {@code INV-04} 的状态，
     * 由 {@code extraTrading} 开关直接插库造出（{@code ShopFixtures} 的正当用途之一）。
     */
    private Ctx prepare(String goodsSetup, String targetKind, boolean extraTrading) throws Exception {
        boolean manual = "manual".equals(goodsSetup);
        boolean noGoods = "NONE".equals(goodsSetup);

        String goodsId = noGoods
                ? null
                : fixtures.insertGoods(manual ? "frozen" : "on_sale", manual ? "manual" : null,
                        "用例商品", "12.50");

        List<String> ids = new ArrayList<>();
        if (goodsId != null && !"none".equals(targetKind)) {
            ids.add(fixtures.insertIntention(goodsId, "queued"));
            ids.add(fixtures.insertIntention(goodsId, "queued"));
        }
        if (goodsId != null && extraTrading) {
            fixtures.insertIntention(goodsId, "trading");
        }

        String head = ids.isEmpty() ? null : ids.get(0);
        if ("trade".equals(goodsSetup)) {
            enterTrade(head);
        }

        String targetId = switch (targetKind) {
            case "head" -> head == null ? NO_SUCH_INTENTION : head;
            case "second" -> ids.get(1);
            case "none" -> NO_SUCH_INTENTION;
            case "missing" -> NO_SUCH_INTENTION;
            default -> throw new IllegalArgumentException("用例表的 targetKind 非法: " + targetKind);
        };
        return new Ctx(goodsId, head, ids.size() > 1 ? ids.get(1) : null, targetId);
    }

    /** 调 {@code I11-11} 并要求成功（夹具用，失败即用例本身准备不成立）。 */
    private void enterTrade(String intentionId) throws Exception {
        mockMvc.perform(authorizedPost(SellerIntentionController.TRADE_PATH, intentionIdBody(intentionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 调 {@code I11-13} 并要求成功。 */
    private void markFailure(String intentionId, String disposal, String failReason) throws Exception {
        mockMvc.perform(authorizedPost(SellerIntentionController.FAILURE_PATH,
                        failureBody(intentionId, disposal, failReason)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private MockHttpServletRequestBuilder authorizedGet(String path) {
        return get(path).header("Authorization", "Bearer " + token);
    }

    private MockHttpServletRequestBuilder authorizedPost(String path, String jsonBody) {
        return post(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody);
    }

    /** 用 Jackson 构造请求体，而不是字符串拼接（{@code fail_reason} 里可能有换行）。 */
    private String intentionIdBody(String intentionId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("intention_id", intentionId);
        return objectMapper.writeValueAsString(body);
    }

    private String failureBody(String intentionId, String disposal, String failReason) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("intention_id", intentionId);
        body.put("disposal", CsvValues.expand(disposal));
        body.put("fail_reason", CsvValues.expand(failReason));
        return objectMapper.writeValueAsString(body);
    }

    private static String passcodeQuery(String intentionId) {
        return SellerIntentionController.PASSCODE_PATH + "?intention_id=" + intentionId;
    }

    /** 某意向的流水条数。 */
    private int flowCount(String intentionId) {
        return fixtures.count("select count(*) from simpleshop_trade_history where fk_intention_id = ?",
                intentionId);
    }

    /**
     * 取意向行——<b>先查当前表，再查历史表</b>。
     *
     * <p>「标记交易成功」会在<b>同一事务内归档</b>（当前表清空、行搬到历史表），
     * 因此断言目标意向的最终状态时不能只查当前表，否则会得到
     * {@code Incorrect result size: expected 1, actual 0} 这种与被测逻辑无关的错误。
     */
    private Map<String, Object> intentionRowAnywhere(String intentionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select * from simpleshop_intentions where id = ?", intentionId);
        return rows.isEmpty() ? fixtures.intentionRowInHistory(intentionId) : rows.get(0);
    }
}
