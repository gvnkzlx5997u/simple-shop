package com.simpleshop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.simpleshop.service.SellerGoodsService;
import com.simpleshop.service.SellerIntentionService;
import com.simpleshop.session.SessionStore;
import com.simpleshop.testing.ShopFixtures;

/**
 * 历史商品接口的契约与取数验证（{@code I11-15}、{@code I11-16}）——S8 的验收凭据。
 *
 * <p>覆盖方案 §3.3.4、§8.3 的「历史接口」小节与 JSON 契约断言 #1／#2／#3／#5／#7，
 * 以及 §12 的 {@code AC-14}／{@code AC-15}（历史可查、每次失败都能查到）。
 *
 * <h2>⚠️ 本类最要紧的一条：**不要断言「同一秒内的先后」**</h2>
 * <p>排序键（{@code trade_end}／{@code create_at}／{@code trade_start}）都是<b>秒精度</b>
 * {@code datetime}（V1 脚本）。测试里连续造的几行落在同一秒内，它们的先后<b>没有确定顺序</b>
 * ——断言顺序的用例会偶发失败（S7 已经踩过一次，见 §8.12 O-1）。
 * <p>故本类的做法是：<b>凡是要断言顺序的用例，都先把排序键改成互不相同的值</b>
 * （见 {@link #listHistoryIsOrderedByTradeEndDesc()} 与
 * {@link #detailIntentionsAreOrderedByCreateAtAsc()}），而不是指望「刚造出来的数据恰好有序」。
 */
@SpringBootTest
@ActiveProfiles("test")
class SellerHistoryApiTest {

    private static final String ACCOUNT = "seller";

    /** 契约要求的时间形状（方案 §8.3 断言 #3）。 */
    private static final String CONTRACT_TIME_SHAPE = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z";

    /** {@code I11-15} 的 {@code items[]} 字段集——**恰好 5 个**。 */
    private static final Set<String> LIST_ITEM_FIELDS =
            Set.of("id", "name", "create_at", "trade_end", "result");

    /** {@code I11-16} 的商品字段集——**恰好 9 个**（另加 {@code intentions}）。 */
    private static final Set<String> DETAIL_FIELDS = Set.of(
            "id", "name", "description", "pic_url", "price", "status",
            "create_at", "trade_end", "result", "intentions");

    /** {@code I11-16} 的 {@code intentions[]} 字段集——**恰好 9 个**（含 {@code trades}）。 */
    private static final Set<String> INTENTION_FIELDS = Set.of(
            "id", "name", "tel", "create_at", "status", "fail_type", "fail_reason",
            "trade_count", "trades");

    /** {@code I11-16} 的 {@code trades[]} 字段集——**恰好 5 个**。 */
    private static final Set<String> TRADE_FIELDS =
            Set.of("trade_start", "trade_end", "result", "fail_type", "fail_reason");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private SellerGoodsService sellerGoodsService;

    @Autowired
    private SellerIntentionService sellerIntentionService;

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
    // I11-15 历史商品列表
    // =========================================================================

    @Test
    @DisplayName("I11-15 无历史：data 是对象而不是 null，total=0 且 items 为空数组")
    void listHistoryWithoutHistoryIsAnEmptyPageNotNullData() throws Exception {
        mockMvc.perform(authorizedGet(SellerHistoryController.PRODUCTS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.page_size").value(10))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                // ⚠️ 与 I11-04／I11-10 不同：历史为空是【正常初始状态】，data 不得为 null
                //（data:null 在本项目里一直是「当前无商品」的专用表达）
                .andExpect(jsonPath("$.data").value(not(nullValue())))
                .andExpect(content().string(not(containsString("\"data\":null"))));
    }

    @Test
    @DisplayName("I11-15 按 trade_end 倒序，且 items[] 恰好 5 个字段（不含 trade_start/description/price）")
    void listHistoryIsOrderedByTradeEndDesc() throws Exception {
        // 三件商品走【真实归档路径】下架；但归档发生在同一秒内，
        // 排序键相同 ⇒ 顺序不确定，故随后把 trade_end 显式改成互不相同的值。
        // （这正是 §8.12 O-1 的同一类事实：秒精度列不能区分同一秒内的先后。）
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String goodsId = fixtures.insertGoods("on_sale", null, "历史商品" + i, "9.90");
            sellerGoodsService.takeGoodsOffline();
            ids.add(goodsId);
        }
        LocalDateTime base = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(30);
        for (int i = 0; i < 3; i++) {
            // ids[0] 最早、ids[2] 最新
            jdbcTemplate.update("update simpleshop_goods_history set trade_end = ? where id = ?",
                    base.plusSeconds(i), ids.get(i));
        }

        String body = mockMvc.perform(authorizedGet(SellerHistoryController.PRODUCTS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                // 倒序：最新（ids[2]）在最前
                .andExpect(jsonPath("$.data.items[0].id").value(ids.get(2)))
                .andExpect(jsonPath("$.data.items[1].id").value(ids.get(1)))
                .andExpect(jsonPath("$.data.items[2].id").value(ids.get(0)))
                .andExpect(jsonPath("$.data.items[0].result").value("offline"))
                .andExpect(jsonPath("$.data.items[0].create_at").value(
                        org.hamcrest.Matchers.matchesPattern(CONTRACT_TIME_SHAPE)))
                .andExpect(jsonPath("$.data.items[0].trade_end").value(
                        org.hamcrest.Matchers.matchesPattern(CONTRACT_TIME_SHAPE)))
                .andReturn().getResponse().getContentAsString();

        // 字段集：不多不少（用 JsonNode 取键集，比逐字段断言更能发现「多带了一列」）
        JsonNode first = objectMapper.readTree(body).path("data").path("items").get(0);
        assertThat(fieldNames(first)).isEqualTo(LIST_ITEM_FIELDS);
        // 契约明确不展示的东西，一个都不该出现（澄清 Q22）
        assertThat(body).doesNotContain("trade_start");
    }

    @Test
    @DisplayName("I11-15 分页：默认每页 10 条（10-E），第 2 页给出余数，total 是总数")
    void listHistoryPaging() throws Exception {
        for (int i = 0; i < 12; i++) {
            fixtures.insertGoods("on_sale", null, "历史商品" + i, "9.90");
            sellerGoodsService.takeGoodsOffline();
        }

        mockMvc.perform(authorizedGet(SellerHistoryController.PRODUCTS_PATH))
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.page_size").value(10))
                .andExpect(jsonPath("$.data.items.length()").value(10));

        mockMvc.perform(authorizedGet(SellerHistoryController.PRODUCTS_PATH + "?page=2&page_size=10"))
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2));

        mockMvc.perform(authorizedGet(SellerHistoryController.PRODUCTS_PATH + "?page=2"))
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.page_size").value(10))
                .andExpect(jsonPath("$.data.items.length()").value(2));

        // 契约只给 page／page_size 两个入参：**不支持任何筛选**（FR-11）——
        // 多传的参数必须被忽略（而不是「碰巧当成筛选条件」或报错）
        mockMvc.perform(authorizedGet(SellerHistoryController.PRODUCTS_PATH + "?result=sold&name=whatever"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.items.length()").value(10));
    }

    // =========================================================================
    // I11-16 历史商品详情
    // =========================================================================

    @Test
    @DisplayName("I11-16 商品 9 字段 + intentions[]：status 恒 off_sale、price 是两位小数字符串")
    void detailReturnsGoodsFieldsAndIntentions() throws Exception {
        String goodsId = fixtures.insertGoods("on_sale", null, "历史商品详情", "9.90");
        jdbcTemplate.update("update simpleshop_goods set description = ?, pic_url = ? where id = ?",
                "描述-历史详情", "/images/2026/10/history-case.jpg", goodsId);
        fixtures.insertIntention(goodsId, 1, "succeeded", "HISTORY00001");
        fixtures.insertIntention(goodsId, 2, "failed", "HISTORY00002");
        sellerGoodsService.takeGoodsOffline();

        String body = mockMvc.perform(authorizedGet(detailPath(goodsId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(goodsId))
                .andExpect(jsonPath("$.data.name").value("历史商品详情"))
                .andExpect(jsonPath("$.data.description").value("描述-历史详情"))
                .andExpect(jsonPath("$.data.pic_url").value("/images/2026/10/history-case.jpg"))
                // ⚠️ price 是 JSON 字符串、两位小数（11-F）
                .andExpect(jsonPath("$.data.price").value("9.90"))
                // ⚠️ 归档例外①：history.status 恒写 off_sale
                .andExpect(jsonPath("$.data.status").value("off_sale"))
                .andExpect(jsonPath("$.data.create_at").value(
                        org.hamcrest.Matchers.matchesPattern(CONTRACT_TIME_SHAPE)))
                .andExpect(jsonPath("$.data.trade_end").value(
                        org.hamcrest.Matchers.matchesPattern(CONTRACT_TIME_SHAPE)))
                .andExpect(jsonPath("$.data.result").value("offline"))
                .andExpect(jsonPath("$.data.intentions.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        assertThat(fieldNames(data)).isEqualTo(DETAIL_FIELDS);

        // 历史意向的字段集（含 trades[]；⚠️ 不含 queue_order/token）
        JsonNode intention = data.path("intentions").get(0);
        assertThat(fieldNames(intention)).isEqualTo(INTENTION_FIELDS);
        // 「商品部分不展示 trade_start」（澄清 Q22）由上面的 DETAIL_FIELDS 断言覆盖
        // （⚠️ 不能用 body 级扫描来断言它——trades[] 里的 trade_start 是契约要求的字段）。
        // 这里只额外排除两个【从未登记】的列名：历史实体上有、但 §11.5 的字段表里没有。
        assertThat(body).doesNotContain("freeze_by").doesNotContain("update_at");
    }

    @Test
    @DisplayName("I11-16 ⚠️ N 次失败逐条可见：trade_count == trades.length()，且不含 token/queue_order")
    void detailExposesEveryTradeAndNoSecrets() throws Exception {
        String goodsId = fixtures.insertGoods("on_sale", null, "两次失败的商品", "9.90");
        String intention = fixtures.insertIntention(goodsId, 1, "queued", "TWOTRADES001");
        String secretToken = fixtures.tokenOf(intention);

        // 走真实业务路径：进入交易 → 重新排队 → 再次进入交易 → 作废（⇒ 2 条流水）
        sellerIntentionService.enterTrade(intention);
        sellerIntentionService.markTradeFailure(intention, "requeued", "买家要求改期");
        sellerIntentionService.enterTrade(intention);
        sellerIntentionService.markTradeFailure(intention, "voided", "履约失败");
        // 队列已空 → 手动下架把它归档（历史里留下这条「失败过两次」的意向）
        sellerGoodsService.takeGoodsOffline();

        String body = mockMvc.perform(authorizedGet(detailPath(goodsId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("offline"))
                .andExpect(jsonPath("$.data.intentions.length()").value(1))
                .andExpect(jsonPath("$.data.intentions[0].status").value("failed"))
                .andExpect(jsonPath("$.data.intentions[0].fail_type").value("voided"))
                .andExpect(jsonPath("$.data.intentions[0].trade_count").value(2))
                .andExpect(jsonPath("$.data.intentions[0].trades.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        JsonNode intentionNode = objectMapper.readTree(body).path("data").path("intentions").get(0);
        // ⚠️ trade_count 与 trades.length 必须相等（§8.3 第 13 条；两者是分别取的）
        assertThat(intentionNode.path("trade_count").asInt())
                .isEqualTo(intentionNode.path("trades").size())
                .isEqualTo(2);
        // 每一次失败都能查到（BR-22、澄清 Q15）——⚠️ 但【不】断言先后（秒精度，见类注释）
        List<String> failTypes = new ArrayList<>();
        for (JsonNode trade : intentionNode.path("trades")) {
            assertThat(fieldNames(trade)).isEqualTo(TRADE_FIELDS);
            assertThat(trade.path("result").asText()).isEqualTo("failed");
            failTypes.add(trade.path("fail_type").asText());
        }
        assertThat(failTypes).containsExactlyInAnyOrder("requeued", "voided");
        assertThat(intentionNode.path("trades").get(0).path("trade_start").asText())
                .matches(CONTRACT_TIME_SHAPE);

        // ⚠️ 安全项（11-I）：历史详情【绝不】返回 queue_order 与 token（用字符串扫描断言）
        assertThat(body).doesNotContain("queue_order").doesNotContain(secretToken);
    }

    @Test
    @DisplayName("I11-16 intentions[] 按 create_at 升序（11-H）——用互不相同的 create_at 才可断言")
    void detailIntentionsAreOrderedByCreateAtAsc() throws Exception {
        String goodsId = fixtures.insertGoods("on_sale", null, "三名买家的商品", "9.90");
        String first = fixtures.insertIntention(goodsId, 1, "revoked", "ORDERED00001");
        String second = fixtures.insertIntention(goodsId, 2, "revoked", "ORDERED00002");
        String third = fixtures.insertIntention(goodsId, 3, "revoked", "ORDERED00003");
        sellerGoodsService.takeGoodsOffline();

        // 归档后的 create_at 都落在同一秒 → 顺序不确定（见类注释）。故显式改成互不相同的值：
        // first 最早、third 最晚。用「历史表」而不是当前表——当前表已经被归档清空了。
        LocalDateTime base = LocalDateTime.now(ZoneOffset.UTC).minusHours(3);
        jdbcTemplate.update("update simpleshop_intentions_history set create_at = ? where id = ?",
                base, first);
        jdbcTemplate.update("update simpleshop_intentions_history set create_at = ? where id = ?",
                base.plusHours(1), second);
        jdbcTemplate.update("update simpleshop_intentions_history set create_at = ? where id = ?",
                base.plusHours(2), third);

        mockMvc.perform(authorizedGet(detailPath(goodsId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intentions[0].id").value(first))
                .andExpect(jsonPath("$.data.intentions[1].id").value(second))
                .andExpect(jsonPath("$.data.intentions[2].id").value(third));
    }

    @Test
    @DisplayName("I11-16 历史商品不存在 → 20011 + HTTP 404（归商品域，不越域占用 4xxxx）")
    void detailUnknownIdReturns404With20011() throws Exception {
        mockMvc.perform(authorizedGet(detailPath("GNO-SUCH-HISTORY")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(20011))
                .andExpect(jsonPath("$.message").value("history product not found"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("I11-16 归档后的两种 result（sold/offline）都能在列表里看到")
    void bothResultsAreVisibleInHistory() throws Exception {
        // offline 路径
        String offlineGoods = fixtures.insertGoods("on_sale", null, "下架的商品", "9.90");
        fixtures.insertIntention(offlineGoods, 1, "revoked", "RESULTOFF001");
        sellerGoodsService.takeGoodsOffline();

        // sold 路径：进交易 → 标记成功
        String soldGoods = fixtures.insertGoods("on_sale", null, "成交的商品", "19.90");
        String head = fixtures.insertIntention(soldGoods, 2, "queued", "RESULTPAID01");
        sellerIntentionService.enterTrade(head);
        sellerIntentionService.markTradeSuccess(head);

        mockMvc.perform(authorizedGet(detailPath(offlineGoods)))
                .andExpect(jsonPath("$.data.result").value("offline"))
                .andExpect(jsonPath("$.data.intentions[0].status").value("revoked"));
        mockMvc.perform(authorizedGet(detailPath(soldGoods)))
                .andExpect(jsonPath("$.data.result").value("sold"))
                .andExpect(jsonPath("$.data.intentions[0].status").value("succeeded"))
                .andExpect(jsonPath("$.data.intentions[0].trades[0].result").value("sold"));
    }

    // =========================================================================
    // 鉴权、路径与参数契约
    // =========================================================================

    @Test
    @DisplayName("两个端点都要求 token：无 Authorization → 401 + 10002")
    void bothEndpointsRequireToken() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                get(SellerHistoryController.PRODUCTS_PATH),
                get(detailPath("GANY-ID")))) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(10002));
        }
    }

    @ParameterizedTest(name = "I11-15 分页参数越界：{0} → 400 + 50002")
    @CsvSource({"page=0", "page_size=0", "page_size=101"})
    @DisplayName("I11-15 分页参数越界 → HTTP 400 + 50002（⚠️不是 500）")
    void listHistoryRejectsBadPaging(String query) throws Exception {
        mockMvc.perform(authorizedGet(SellerHistoryController.PRODUCTS_PATH + "?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50002));
    }

    @Test
    @DisplayName("路径与方法：I11-16 是唯一带路径参数的接口；动词错配 → 405")
    void pathsAndMethods() throws Exception {
        assertThat(SellerHistoryController.PRODUCTS_PATH).isEqualTo("/api/seller/history/products");

        mockMvc.perform(authorizedPost(SellerHistoryController.PRODUCTS_PATH, "{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(authorizedGet(detailPath("GX")))
                .andExpect(jsonPath("$.code").value(20011));
    }

    // =========================================================================
    // 用例辅助
    // =========================================================================

    private static String detailPath(String goodsId) {
        return SellerHistoryController.PRODUCTS_PATH + "/" + goodsId;
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

    /** JSON 对象的键集（用于「字段不多不少」的断言）。 */
    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new java.util.LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
