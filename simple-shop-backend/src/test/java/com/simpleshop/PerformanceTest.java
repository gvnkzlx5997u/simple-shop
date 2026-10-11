package com.simpleshop;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.simpleshop.service.SellerGoodsService;
import com.simpleshop.service.SellerHistoryService;
import com.simpleshop.service.SellerIntentionService;
import com.simpleshop.service.dto.HistoryGoodsDetailData;
import com.simpleshop.service.dto.IntentionPageData;
import com.simpleshop.testing.ShopFixtures;

/**
 * 性能实测（方案 §8.4、{@code 12-P1}）——S10 的验收凭据。
 *
 * <h2>指标与条件（照抄 §8.4，不得放宽）</h2>
 * <table border="1">
 *   <caption>§8.4 的两条指标</caption>
 *   <tr><th>类别</th><th>目标</th><th>条件</th></tr>
 *   <tr><td>查询类响应</td><td><b>P95 ≤ 500ms</b></td>
 *       <td rowspan="2">1 件商品 + <b>1000 条意向</b> + <b>100 条历史</b></td></tr>
 *   <tr><td>写操作响应</td><td><b>P95 ≤ 1s</b></td></tr>
 * </table>
 *
 * <h2>⚠️ 为什么把绝对时间当作断言（而不是只打印数字）</h2>
 * <p>计时断言天然依赖机器。但这里的两个边界（500ms／1s）比<b>实测值高出一到两个数量级</b>
 * （见下），因此它<b>不会</b>因为机器快慢而颤动，只会在出现<b>数量级</b>级别的退化时失败——
 * 例如有人把 {@code I11-16} 的「逐条查流水」改成「对每条意向做一次全表扫描」，
 * 或者忘了给 {@code queue_order} 的计数走索引。这正是 {@code 12-P1} 想拦住的东西。
 * <p>⚠️ 本类<b>不</b>测量 HTTP 层（MockMvc/容器开销与本类要测的 SQL 规模无关）：
 * 直接调 Service，把测量对象收敛到「数据库与取数口径」。
 *
 * <h2>⚠️ 本类测的是「两个已知风险点」</h2>
 * <ol>
 *   <li>{@code I11-10}（意向名单）：每页 10 条 → 最多 <b>10 次</b> {@code countQueuedAhead}，
 *       再叠加 1 次分页查询 + 1 次非终态计数（方案 §3.3.3 已论证其可接受）；</li>
 *   <li>{@code I11-16}（历史详情）：对每条历史意向各查一次流水 → <b>N+1</b>（方案 §3.3.4 的取舍）。
 *       本类为此专门造了一条<b>最重的历史商品</b>（{@value #HEAVY_INTENTIONS} 条意向），
 *       因为 N+1 的规模取决于<b>单件商品</b>的意向数，而不是历史商品总数。</li>
 * </ol>
 *
 * <h2>⚠️ 数据规模与「真实业务规模」的关系（写清楚，免得被误读）</h2>
 * <p>1000 条意向是 {@code NFR-07} 的<b>队列上限</b>，即「最坏情况」而非典型值；
 * 100 条历史商品也不是长期运营后的真实量级。本类刻意用<b>上限</b>造数：
 * 若在最坏情况下都远低于指标，典型情况自然更宽松。
 *
 * <h2>⚠️ 「归档整队列」这一项的口径与裁定（§8.14 O-1，业务方已确认）</h2>
 * <p>§8.4 写的是「写操作 P95 ≤ 1s」，但<b>没有说清单个端点还是整个写请求集</b>。
 * 实测发现两条「一次点击就把整队列搬走」的归档写（{@code I11-09}／{@code I11-12}）
 * 在 <b>1000 条意向</b>下要约 1.9~2.8s，而其余写只有 36~61ms。**业务方已裁定：
 * 按「写请求整体的 P95」判定**——即归档的绝对耗时不计入该指标，且经确认<b>可接受</b>，
 * 故<b>不做</b>批量删除优化（其代价见 §8.14 D-1：要绕开持久化上下文、动数据层入口、
 * 并作废 S7 对归档顺序的验证）。
 * <p>因此本类对这两条写的断言<b>不是</b> §8.4 的 1s，而是两条与机器无关的判据：
 * ① 结构性——INSERT 必须仍走批处理；② 数量级——不得超过 5s（拦灾难性退化）。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PerformanceTest {

    /** §8.4 的查询指标：P95 ≤ 500ms。 */
    private static final long QUERY_P95_BUDGET_MS = 500;

    /** §8.4 的写操作指标：P95 ≤ 1s（按「写请求整体 P95」判定，见类注释与 §8.14 O-1）。 */
    private static final long WRITE_BUDGET_MS = 1000;

    /** 当前商品的意向数（{@code NFR-07} 的队列上限，即最坏情况）。 */
    private static final int CURRENT_INTENTIONS = 1000;

    /** 历史商品数（§8.4 的造数条件）。 */
    private static final int HISTORY_GOODS = 100;

    /** 普通历史商品的意向数。 */
    private static final int HISTORY_INTENTIONS = 5;

    /** 「最重的历史商品」的意向数——用来压 {@code I11-16} 的 N+1。 */
    private static final int HEAVY_INTENTIONS = 50;

    /** 每个端点测量多少次（够算 P95，又不至于让用例变慢）。 */
    private static final int ITERATIONS = 30;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SellerIntentionService sellerIntentionService;

    @Autowired
    private SellerHistoryService sellerHistoryService;

    @Autowired
    private SellerGoodsService sellerGoodsService;

    private ShopFixtures fixtures;

    /** 当前商品 ID（1000 条意向挂在它名下）。 */
    private String goodsId;

    /** 最重的历史商品 ID（{@value #HEAVY_INTENTIONS} 条意向）。 */
    private String heavyHistoryGoodsId;

    @BeforeEach
    void setUp() {
        fixtures = new ShopFixtures(jdbcTemplate);
        fixtures.cleanGoodsState();
        fixtures.resetSequence();
        buildContractDataset();
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanGoodsState();
    }

    // =========================================================================
    // 查询类
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("§8.4 查询 P95 ≤ 500ms：I11-10（1000 条意向）/ I11-15（100 条历史）/ I11-16（N+1 最重件）")
    void queriesStayWithinBudgetAtP95() {
        // ---- I11-10：意向名单（每页 10 条 → 10 次 countQueuedAhead）----
        long p95List = measure("I11-10 意向名单（1000 条意向）",
                () -> sellerIntentionService.listIntentions(1, 10));
        assertThat(p95List)
                .as("I11-10 的 P95 必须 ≤ %d ms（12-P1）", QUERY_P95_BUDGET_MS)
                .isLessThanOrEqualTo(QUERY_P95_BUDGET_MS);

        // ---- I11-15：历史列表（100 条历史）----
        long p95History = measure("I11-15 历史列表（100 条历史）",
                () -> sellerHistoryService.listHistory(1, 10));
        assertThat(p95History)
                .as("I11-15 的 P95 必须 ≤ %d ms（12-P1）", QUERY_P95_BUDGET_MS)
                .isLessThanOrEqualTo(QUERY_P95_BUDGET_MS);

        // ---- I11-16：历史详情（最重件：N 条意向 → N 次流水查询 + 2）----
        long p95Detail = measure("I11-16 历史详情（%d 条意向的 N+1）".formatted(HEAVY_INTENTIONS),
                () -> sellerHistoryService.getHistoryDetail(heavyHistoryGoodsId));
        assertThat(p95Detail)
                .as("I11-16 的 P95 必须 ≤ %d ms（12-P1）；若不达标，按 §3.3.4 的后路在数据层新增批量查询",
                        QUERY_P95_BUDGET_MS)
                .isLessThanOrEqualTo(QUERY_P95_BUDGET_MS);

        // 顺带把「测的确实是那个规模」钉住——否则数据没造对，上面的时间毫无意义
        IntentionPageData page = sellerIntentionService.listIntentions(1, 10);
        assertThat(page.total()).isEqualTo(CURRENT_INTENTIONS);
        assertThat(page.queueCount()).isEqualTo(CURRENT_INTENTIONS);
        assertThat(page.items()).hasSize(10);
        HistoryGoodsDetailData detail = sellerHistoryService.getHistoryDetail(heavyHistoryGoodsId);
        assertThat(detail.intentions()).hasSize(HEAVY_INTENTIONS);
    }

    // =========================================================================
    // 写操作类
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("§8.4 写操作 P95 ≤ 1s：典型写（冻结/解冻、进入交易/标记失败）——留有大量余量")
    void typicalWritesStayWithinBudget() {
        // ⚠️ 写操作大多【不可重复】（状态机是单向的），故这里只挑两对天然可循环的：
        //    ① 冻结 ⇄ 解冻；② 进入交易 → 标记失败(重新排队)（重排队把意向放回队首，可再进一次）。
        //    其余写（标记成功、下架归档）不可循环——最重的那一个单独测（见下一个方法）。
        long p95FreezeCycle = measure("写：冻结 + 解冻（各 1 次为 1 样本）", () -> {
            sellerGoodsService.freezeGoods();
            sellerGoodsService.unfreezeGoods();
        });

        long p95TradeCycle = measure("写：进入交易 + 标记失败(重排队)", () -> {
            String head = jdbcTemplate.queryForObject(
                    "select id from simpleshop_intentions order by queue_order asc limit 1", String.class);
            sellerIntentionService.enterTrade(head);
            sellerIntentionService.markTradeFailure(head, "requeued", "性能样本");
        });

        assertThat(p95FreezeCycle).as("典型写的 P95 必须 ≤ %d ms", WRITE_BUDGET_MS)
                .isLessThanOrEqualTo(WRITE_BUDGET_MS);
        assertThat(p95TradeCycle).as("典型写的 P95 必须 ≤ %d ms", WRITE_BUDGET_MS)
                .isLessThanOrEqualTo(WRITE_BUDGET_MS);

        // 循环之后数据规模必须没变——否则上面测的不是同一个规模
        assertThat(fixtures.countIntentions()).isEqualTo(CURRENT_INTENTIONS);
    }

    @Test
    @Order(3)
    @DisplayName("归档整队列的写（1000 条）：实测约 1.9~2.8s；按「写请求整体 P95」判定达标，绝对值经确认可接受（§8.14 O-1）")
    void archiveWritesAtTheQueueCeilingAreTheKnownDeviation() {
        // ---- 路径一：手动下架归档（同时用 MySQL 的语句计数器做结构性核对）----
        jdbcTemplate.update("update simpleshop_intentions set status = 'revoked', fail_type = 'revoked'");
        long insertBefore = globalStatus("Com_insert");
        long deleteBefore = globalStatus("Com_delete");
        long offlineMs = measureOnce("I11-09 下架归档（1000 条）", () -> sellerGoodsService.takeGoodsOffline());
        long insertDelta = globalStatus("Com_insert") - insertBefore;
        long deleteDelta = globalStatus("Com_delete") - deleteBefore;
        assertArchivedCompletely();
        rebuildDataset();

        // ---- 路径二：标记成功归档（一次点击就把整队列连带失败后归档，比下架更常见）----
        long markSuccessMs = measureOnce("I11-12 标记成功归档（1000 条）", () -> {
            String head = jdbcTemplate.queryForObject(
                    "select id from simpleshop_intentions order by queue_order asc limit 1", String.class);
            sellerIntentionService.enterTrade(head);
            sellerIntentionService.markTradeSuccess(head);
        });
        assertArchivedCompletely();

        System.out.printf(Locale.ROOT,
                "[perf] 归档 1000 条意向的语句计数：Com_insert 增量=%d（批处理生效时应 ≈ %d），"
                        + "Com_delete 增量=%d（⚠️ 实测 = 行数 + 1，即【未】批处理，见 §8.14 O-1）%n",
                insertDelta, CURRENT_INTENTIONS / 50 + 2, deleteDelta);

        // ⚠️⚠️ 这里【不】断言 §8.4 的 1s —— 该指标按「写请求整体的 P95」判定（§8.14 O-1，业务方已确认）：
        //    归档是「一个商品生命周期一次」的操作，其绝对耗时经确认可接受，且不做批量删除优化。
        //    但也不把这两条写放任不管：拿绝对时间当护栏会随机器负载颤动（同配置单跑/全量跑能差 50%+），
        //    故本用例守两件【不随机器漂】的事：
        //    ①【结构性】INSERT 必须仍被批处理 —— 关掉 batch_size 会让它从 ~21 涨到 ~1000，
        //      这个判据与机器快慢完全无关；
        //    ②【数量级】时间不得超过 5s —— 只拦灾难性退化（例如把取数改成 O(N²)）。
        assertThat(insertDelta)
                .as("归档的 INSERT 必须走 JDBC 批处理（batch_size=50）：1000 行应约 21 条语句；"
                        + "若涨到 ~1000，说明 hibernate.jdbc.batch_size 被误删（§8.14 的 B-2）")
                .isLessThanOrEqualTo(CURRENT_INTENTIONS / 50 + 5);
        assertThat(Math.max(offlineMs, markSuccessMs))
                .as("归档整队列必须 ≤ 5000 ms（数量级护栏；§8.4 的 1s 指标按「写请求整体 P95」判定，"
                        + "归档一项经确认可接受，见 §8.14 O-1）")
                .isLessThanOrEqualTo(5000);
    }

    /** 读 MySQL 的全局语句计数器（测试专用；本套件串行执行，测量窗口内没有其它写入）。 */
    private long globalStatus(String name) {
        Long value = jdbcTemplate.queryForObject(
                "show global status like '" + name + "'", (rs, rowNum) -> rs.getLong(2));
        return value == null ? -1 : value;
    }

    /** 归档后自检：该商品的意向必须全部搬进历史表、当前表必须空。 */
    private void assertArchivedCompletely() {
        assertThat(fixtures.count("select count(*) from simpleshop_intentions_history "
                + "where fk_goods_history_id = ?", goodsId))
                .as("归档必须真的搬完 %d 条", CURRENT_INTENTIONS).isEqualTo(CURRENT_INTENTIONS);
        assertThat(fixtures.countGoods()).isZero();
        assertThat(fixtures.countIntentions()).isZero();
    }

    /** 重造 §8.4 的数据集（归档会把商品与意向整体搬走，第二次测量需要新的现场）。 */
    private void rebuildDataset() {
        fixtures.cleanGoodsState();
        fixtures.resetSequence();
        buildContractDataset();
    }

    // =========================================================================
    // 造数与测量
    // =========================================================================

    /**
     * 按 §8.4 的条件造数：1 件当前商品 + 1000 条意向 + 100 条历史（外加一条最重的历史商品）。
     *
     * <p>⚠️ 历史行用 JDBC 批量直插，<b>不</b>走归档业务路径——本类测的是「读的规模」，
     * 造 100 条历史若走真实归档需要 100 次「发布 + 下架」，纯属浪费且与本类目的无关。
     * （归档路径本身的正确性由 {@code ArchiveServiceTest} 逐列核对，不在这里重复。）
     */
    private void buildContractDataset() {
        // ---- 当前商品 + 1000 条 queued 意向 ----
        goodsId = fixtures.insertGoods("on_sale", null, "性能用商品", "9.90");
        fixtures.insertIntentions(goodsId, CURRENT_INTENTIONS, "queued");

        // ---- 100 条历史商品（含一条「最重的」）----
        LocalDateTime base = LocalDateTime.now(ZoneOffset.UTC).minusDays(HISTORY_GOODS + 1);
        List<Object[]> goodsRows = new ArrayList<>(HISTORY_GOODS);
        List<Object[]> intentionRows = new ArrayList<>();
        String heavyId = null;
        for (int i = 0; i < HISTORY_GOODS; i++) {
            String historyId = "GHIST" + String.format("%04d", i);
            LocalDateTime createAt = base.plusDays(i);
            LocalDateTime tradeEnd = createAt.plusHours(1);
            goodsRows.add(new Object[]{
                    historyId, "历史商品" + i, "描述" + i, null, new BigDecimal("9.90"),
                    "off_sale", null, createAt, tradeEnd, createAt, tradeEnd,
                    i % 2 == 0 ? "sold" : "offline"});
            int intentionCount = (i == 0) ? HEAVY_INTENTIONS : HISTORY_INTENTIONS;
            if (i == 0) {
                heavyId = historyId;
            }
            for (int j = 0; j < intentionCount; j++) {
                intentionRows.add(new Object[]{
                        "IHIST" + String.format("%04d", i) + String.format("%03d", j),
                        historyId, createAt, "历史买家" + j, "13800000000", "revoked", "revoked", null});
            }
        }
        jdbcTemplate.batchUpdate(
                "insert into simpleshop_goods_history "
                        + "(id,name,description,pic_url,price,status,freeze_by,create_at,update_at,"
                        + "trade_start,trade_end,result) values (?,?,?,?,?,?,?,?,?,?,?,?)",
                goodsRows);
        jdbcTemplate.batchUpdate(
                "insert into simpleshop_intentions_history "
                        + "(id,fk_goods_history_id,create_at,name,tel,status,fail_type,fail_reason) "
                        + "values (?,?,?,?,?,?,?,?)",
                intentionRows);
        heavyHistoryGoodsId = heavyId;

        // 造完自检：规模不对的话，后面测出来的时间没有意义
        assertThat(fixtures.countIntentions()).isEqualTo(CURRENT_INTENTIONS);
        assertThat(fixtures.count("select count(*) from simpleshop_goods_history")).isEqualTo(HISTORY_GOODS);
        assertThat(fixtures.count("select count(*) from simpleshop_intentions_history"))
                .isEqualTo((HISTORY_GOODS - 1) * HISTORY_INTENTIONS + HEAVY_INTENTIONS);
    }

    /**
     * 测一个<b>不可重复</b>的写：只取一次样本（打印耗时后返回毫秒数）。
     *
     * <p>为什么不做 P95：归档会把商品与意向整体搬走，同一个现场只能归档一次；
     * 反复重建现场测的是「同一段代码的多次独立执行」，而机器状态（MySQL 缓冲池、
     * Docker 的文件系统）在数秒内变化很大——实测同一配置下的重复样本可在 1.4~2.2s 之间浮动。
     * 与其用几个样本假装成一个 P95，不如**只报一次实测值**并说清它的波动范围（§8.14 O-1）。
     */
    private long measureOnce(String label, Runnable call) {
        long start = System.nanoTime();
        call.run();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf(Locale.ROOT, "[perf] %-46s 单次样本=%d ms"
                        + "（§8.4 的 1s 预算按「写请求整体 P95」判定；归档一项见 §8.14 O-1）%n",
                label, elapsedMs);
        return elapsedMs;
    }

    /**
     * 测量一个只读调用的耗时，返回 <b>P95</b>（毫秒），并打印 P50／P95／max 供记录引用。
     *
     * <p>先预热 {@value #ITERATIONS} 次的一半再计时：JIT、连接池、MySQL 的 buffer pool
     * 都需要一点时间进入稳定态，不预热会把「第一次调用」的冷启动算进 P95。
     */
    private long measure(String label, Runnable call) {
        for (int i = 0; i < 10; i++) {
            call.run();
        }
        long[] samples = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            call.run();
            samples[i] = (System.nanoTime() - start) / 1_000_000;
        }
        java.util.Arrays.sort(samples);
        long p50 = samples[samples.length / 2];
        long p95 = samples[(int) Math.ceil(samples.length * 0.95) - 1];
        long max = samples[samples.length - 1];
        System.out.printf(Locale.ROOT, "[perf] %-46s P50=%d ms  P95=%d ms  max=%d ms（n=%d）%n",
                label, p50, p95, max, ITERATIONS);
        return p95;
    }
}
