package com.simpleshop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.simpleshop.config.AppProperties;
import com.simpleshop.persistence.time.DatabaseTimeProvider;
import com.simpleshop.service.OperationLogService;
import com.simpleshop.session.SessionStore;
import com.simpleshop.testing.AuditLogCapture;
import com.simpleshop.testing.CsvValues;
import com.simpleshop.testing.TestImageDirectory;

/**
 * 商品接口的契约与状态机验证（{@code I11-04} ~ {@code I11-09}）——S4 的验收凭据。
 *
 * <p>覆盖方案 §8.3 的「状态机 `PS-01`~`PS-08`」相关条目、JSON 契约断言，
 * 以及 §12 验收口径中的 `AC-01`／`AC-05`／`AC-06`／`AC-21`／`AC-23`。
 *
 * <h2>三张 CSV 用例表驱动本类</h2>
 * <table border="1">
 *   <caption>用例表与覆盖面</caption>
 *   <tr><th>用例表</th><th>列</th><th>覆盖</th></tr>
 *   <tr><td>{@code goods-operation-cases.csv}</td>
 *       <td>{@code caseName,status,freezeBy,queuedCount,tradingCount,operation,expectedCode}</td>
 *       <td>冻结／解冻／下架三条状态迁移的<b>全部合法与非法组合</b>，含 {@code PS-02}（在售+排队也能冻结）、
 *           {@code PS-06}（交易冻结不可解冻）、{@code BR-09}（队列非空不可下架）、
 *           {@code INV-05}（有 trading 意向时归档被拒）</td></tr>
 *   <tr><td>{@code goods-publish-cases.csv}</td>
 *       <td>{@code caseName,name,description,pic_url,price,expectedHttp,expectedCode}</td>
 *       <td>{@code I11-06} 的字段校验错误码<b>与其 HTTP 状态</b>——这是「DTO 注解不得拦取值规则」
 *           那条硬规则（方案 §4.6.3）的<b>回归护栏</b></td></tr>
 *   <tr><td>图片用例表在 {@code ImageTypeTest}</td><td>—</td><td>魔数识别的边界</td></tr>
 * </table>
 *
 * <h2>⚠️ 每个用例前清空商品相关表，且这是必须的</h2>
 * <p>商品表<b>至多 1 行</b>（{@code INV-01}），而 {@code I11-04} 取的是
 * {@code findFirstByOrderByCreateAtAsc()}——只要库里残留任何一行商品，
 * 「查当前商品」「冻结当前商品」这类用例就会作用在<b>别人</b>的数据上，
 * 用例成败将取决于测试类的执行顺序（单独跑绿、全量跑红）。
 * <p>故 {@link #cleanGoodsState()} 在前后各清一次。它只删商品域的 5 张表，
 * <b>不动</b> {@code simpleshop_users} 与 {@code simpleshop_queue_sequence} 的种子行
 * （后者只把 {@code current_value} 复位）。
 *
 * <h2>⚠️ MockMvc <b>无法</b>验证 Spring 层的 5MB 限制</h2>
 * <p>{@code spring.servlet.multipart.max-file-size} 由真实的 multipart 解析器执行，
 * 超限时抛 {@code MaxUploadSizeExceededException}；而 MockMvc 直接构造
 * {@code MockMultipartHttpServletRequest}（文件已是 {@code MultipartFile} 对象），
 * <b>不经过</b>那个解析器。因此本类里「超大文件被拒」的用例实际验证的是
 * {@link com.simpleshop.service.ImageStorageService} 里的<b>第二道</b>防线
 * （{@code NFR-11} 要求的「后端独立校验」）——这恰恰是更需要被测试覆盖的一道。
 * <p>Spring 那一层由真实端口的冒烟验证覆盖（方案 §8.8 的验证证据）。
 */
@SpringBootTest
@ActiveProfiles("test")
class SellerGoodsApiTest {

    /** 契约要求的时间形状（方案 §8.3 断言 #3）。 */
    private static final String CONTRACT_TIME_SHAPE = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z";

    /** 测试用账号（V2 种子）。 */
    private static final String ACCOUNT = "seller";

    /** 5MB 上限（{@code C-14}）。 */
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private static final String IMAGE_PATH = SellerProductController.BASE_PATH + "/image";

    /** 本用例内分配唯一 queue_order / token 的计数器（两者都有全局唯一约束）。 */
    private static final AtomicInteger SEQUENCE = new AtomicInteger(1);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private AppProperties properties;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private String token;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        cleanGoodsState();
        sessionStore.removeAllFor(ACCOUNT);
        token = sessionStore.create(ACCOUNT).token();
        SEQUENCE.set(1);
    }

    @AfterEach
    void tearDown() {
        cleanGoodsState();
        sessionStore.removeAllFor(ACCOUNT);
        deleteTestImageDirectory();
    }

    // =========================================================================
    // I11-04 查当前商品
    // =========================================================================

    @Test
    @DisplayName("I11-04 无商品：HTTP 200 + code 0 + data 为 null（空态，不是错误）")
    void getCurrentGoodsReturnsNullWhenEmpty() throws Exception {
        mockMvc.perform(authorizedGet(SellerProductController.BASE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$['data']").value(nullValue()))
                // 关键：null 的键必须存在（11.3.1），而不是被 @JsonInclude(NON_NULL) 删掉
                .andExpect(content().string(containsString("\"data\":null")));
    }

    @Test
    @DisplayName("I11-04 有商品：9 个契约字段齐全、snake_case、price 是字符串、freeze_by 为 null")
    void getCurrentGoodsReturnsFullContractShape() throws Exception {
        String id = insertGoods("on_sale", null, "手工曲奇", "12.30");
        jdbcTemplate.update("update simpleshop_goods set description = ?, pic_url = ? where id = ?",
                "好吃又实惠", "/images/2026/10/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.jpg", id);

        String body = mockMvc.perform(authorizedGet(SellerProductController.BASE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.name").value("手工曲奇"))
                .andExpect(jsonPath("$.data.description").value("好吃又实惠"))
                .andExpect(jsonPath("$.data.pic_url")
                        .value("/images/2026/10/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.jpg"))
                .andExpect(jsonPath("$.data.status").value("on_sale"))
                // 在售时 freeze_by 必须下发 null（P10-07 靠它决定「解冻」按钮是否可用）
                .andExpect(jsonPath("$['data']['freeze_by']").value(nullValue()))
                .andExpect(jsonPath("$.data.create_at").value(org.hamcrest.Matchers.matchesPattern(CONTRACT_TIME_SHAPE)))
                .andReturn().getResponse().getContentAsString();

        // ⚠️ price 必须是【JSON 字符串】"12.30"，不是数字 12.30（11-F、C-12）
        assertThat(body).contains("\"price\":\"12.30\"");
        assertThat(body).doesNotContain("\"price\":12.30");
        // trade_start 未进入交易时为 null，但键存在
        assertThat(body).contains("\"trade_start\":null");
        assertThat(body).contains("\"freeze_by\":null");
    }

    @Test
    @DisplayName("I11-04 需要 token：未带 token → 401 + 10002")
    void getCurrentGoodsRequiresToken() throws Exception {
        mockMvc.perform(get(SellerProductController.BASE_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002));
    }

    // =========================================================================
    // I11-06 发布商品：错误码 + HTTP 状态用例表
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0} → HTTP {5} / code {6}")
    @CsvFileSource(resources = "/csv/goods-publish-cases.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("I11-06 字段校验用例表（含 HTTP 状态，钉住「DTO 注解不得拦取值规则」）")
    void publishValidationCases(String caseName, String name, String description, String picUrl,
                                String price, int expectedHttp, int expectedCode) throws Exception {
        // 每次都必须处于「无商品」状态，否则会先撞 20001（前置校验先于字段校验）
        assertThat(countGoods()).as("用例前置：不应存在商品").isZero();

        mockMvc.perform(authorizedPost(SellerProductController.BASE_PATH,
                        publishBody(name, description, picUrl, price)))
                .andExpect(status().is(expectedHttp))
                .andExpect(jsonPath("$.code").value(expectedCode));

        assertThat(countGoods()).as("被拒的发布不得留下商品行").isZero();
    }

    @Test
    @DisplayName("I11-06 发布成功：返回 id，且 I11-04 能查到该商品（初始 on_sale、freeze_by 为 null）")
    void publishSucceedsAndBecomesCurrentGoods() throws Exception {
        String id = publish("手工曲奇", "好吃", null, "12.30");

        assertThat(id).startsWith("G");

        mockMvc.perform(authorizedGet(SellerProductController.BASE_PATH))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("on_sale"))
                .andExpect(jsonPath("$['data']['freeze_by']").value(nullValue()));

        // ⚠️ 当前表的 result 恒为 NULL（DEC-DB-11）——发布路径绝不能写它
        assertThat(goodsRow(id).get("result")).isNull();
        assertThat(goodsRow(id).get("trade_start")).isNull();
        assertThat(goodsRow(id).get("trade_end")).isNull();
    }

    @Test
    @DisplayName("I11-06 已有在售/冻结商品时再发布 → 20001（M10-24、INV-01、AC-23）")
    void publishTwiceReturnsGoodsAlreadyExists() throws Exception {
        publish("第一件", null, null, "10.00");

        mockMvc.perform(authorizedPost(SellerProductController.BASE_PATH,
                        publishBody("第二件", null, null, "20.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20001));

        // AC-01／INV-01：在售/冻结行数恒 ≤ 1
        assertThat(countGoods()).isEqualTo(1);
    }

    @Test
    @DisplayName("I11-06 冻结中的商品也算「已存在」→ 20001（不是只有 on_sale 才拦）")
    void publishWhileFrozenAlsoReturnsGoodsAlreadyExists() throws Exception {
        String id = publish("第一件", null, null, "10.00");
        mockMvc.perform(authorizedPost(SellerProductController.BASE_PATH + "/freeze"))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(goodsRow(id).get("status")).isEqualTo("frozen");

        mockMvc.perform(authorizedPost(SellerProductController.BASE_PATH,
                        publishBody("第二件", null, null, "20.00")))
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    @DisplayName("I11-06 名称首尾空格被 trim 后入库（M10-29 的「首尾不要留空格」落实方式）")
    void publishTrimsName() throws Exception {
        String id = publish("  手工曲奇  ", "  好吃  ", null, " 12.30 ");

        assertThat(goodsRow(id).get("name")).isEqualTo("手工曲奇");
        assertThat(goodsRow(id).get("description")).isEqualTo("好吃");
        assertThat(goodsRow(id).get("price").toString()).isEqualTo("12.30");
    }

    @Test
    @DisplayName("I11-06 未提供描述时入库为 NULL（而不是空串）")
    void publishWithoutDescriptionStoresNull() throws Exception {
        String id = publish("手工曲奇", "   ", null, "10.00");

        assertThat(goodsRow(id).get("description")).isNull();
    }

    @Test
    @DisplayName("I11-06 需要 token：未带 token → 401 + 10002")
    void publishRequiresToken() throws Exception {
        mockMvc.perform(post(SellerProductController.BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody("手工曲奇", null, null, "10.00")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002));
    }

    // =========================================================================
    // I11-05 图片上传
    // =========================================================================

    @Test
    @DisplayName("I11-05 真实 JPEG：返回 /images/yyyy/MM/<uuid>.jpg，且文件确实落盘")
    void uploadRealJpegStoresFileAndReturnsPicUrl() throws Exception {
        String picUrl = uploadAndGetPicUrl("test.jpg", "image/jpeg");

        assertThat(picUrl).matches(
                "^/images/\\d{4}/\\d{2}/[0-9a-fA-F-]{36}\\.jpg$");
        // 文件真的写在配置的目录里（服务端生成的 UUID 文件名）
        assertThat(fileFor(picUrl)).isRegularFile();
    }

    @Test
    @DisplayName("I11-05 真实 PNG：扩展名由【服务端识别的内容】决定，不是原文件名")
    void uploadRealPngUsesDetectedExtension() throws Exception {
        // 原文件名写成 .jpg 但内容是 PNG —— 落盘扩展名必须是 png（服务端识别结果）
        String picUrl = uploadAndGetPicUrl("test.png", "image/jpeg");

        assertThat(picUrl).endsWith(".png");
        assertThat(fileFor(picUrl)).isRegularFile();
    }

    @Test
    @DisplayName("I11-05 内容说话：GIF 声明成 image/png 仍被拒（NFR-11 不看扩展名与 Content-Type）")
    void uploadRejectsGifEvenWhenDeclaredAsPng() throws Exception {
        byte[] gif = "GIF89a-not-a-jpeg-or-png".getBytes(StandardCharsets.US_ASCII);

        mockMvc.perform(upload("fake.png", "image/png", gif))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20009));
    }

    @Test
    @DisplayName("I11-05 真实 JPEG 声明成 application/octet-stream 仍被接受（只看内容）")
    void uploadAcceptsJpegWithMisleadingContentType() throws Exception {
        String picUrl = uploadAndGetPicUrl("test.jpg", "application/octet-stream");

        assertThat(fileFor(picUrl)).isRegularFile();
    }

    @Test
    @DisplayName("I11-05 超过 5MB → 20009（Service 侧第二道防线，NFR-11 的「后端独立校验」）")
    void uploadRejectsOversizedFile() throws Exception {
        // 载荷刻意带上【合法 JPEG 魔数】：这样若有人删掉大小检查，内容检查会放行、
        // 本用例立刻失败。若载荷是随机字节，「被拒」就分不清是尺寸还是内容拦下的。
        byte[] oversized = jpegPaddedTo(MAX_IMAGE_BYTES + 1);

        mockMvc.perform(upload("test.jpg", "image/jpeg", oversized))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20009));
    }

    @Test
    @DisplayName("I11-05 恰好 5MB 的 JPEG 是合法的（边界取闭区间）")
    void uploadAcceptsExactlyFiveMegabytes() throws Exception {
        byte[] atLimit = jpegPaddedTo(MAX_IMAGE_BYTES);

        String picUrl = uploadAndGetPicUrl("test.jpg", "image/jpeg", atLimit);

        assertThat(fileFor(picUrl)).isRegularFile();
    }

    @Test
    @DisplayName("I11-05 空文件 → 20009")
    void uploadRejectsEmptyFile() throws Exception {
        mockMvc.perform(upload("empty.jpg", "image/jpeg", new byte[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20009));
    }

    @Test
    @DisplayName("I11-05 缺少 file 部件 → 400 + 50002（报文不合法，不是「图片不合规」）")
    void uploadWithoutFilePartIsParamInvalid() throws Exception {
        mockMvc.perform(multipart(IMAGE_PATH)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50002));
    }

    @Test
    @DisplayName("I11-05 需要 token：未带 token → 401 + 10002")
    void uploadRequiresToken() throws Exception {
        mockMvc.perform(multipart(IMAGE_PATH)
                        .file(new MockMultipartFile("file", "test.jpg", "image/jpeg",
                                readResource("/images/test.jpg"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    @DisplayName("I11-05 上传后回填到 I11-06：pic_url 入库，且 I11-04 能读回同一个路径")
    void uploadedPicUrlCanBePersisted() throws Exception {
        String picUrl = uploadAndGetPicUrl("test.jpg", "image/jpeg");

        String id = publish("带图商品", null, picUrl, "88.00");

        assertThat(goodsRow(id).get("pic_url")).isEqualTo(picUrl);
        mockMvc.perform(authorizedGet(SellerProductController.BASE_PATH))
                .andExpect(jsonPath("$.data.pic_url").value(picUrl));
    }

    // =========================================================================
    // I11-07 / I11-08 / I11-09 状态迁移用例表
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0} → code {6}")
    @CsvFileSource(resources = "/csv/goods-operation-cases.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("冻结／解冻／下架状态迁移用例表")
    void productOperationCases(String caseName, String status, String freezeBy,
                               int queuedCount, int tradingCount, String operation,
                               int expectedCode) throws Exception {
        String goodsId = null;
        if (status != null) {
            goodsId = insertGoods(status, freezeBy, "状态机用例商品", "10.00");
            for (int i = 0; i < queuedCount; i++) {
                insertIntention(goodsId, "queued");
            }
            for (int i = 0; i < tradingCount; i++) {
                insertIntention(goodsId, "trading");
            }
        }
        Object freezeByBefore = goodsId == null ? null : goodsRow(goodsId).get("freeze_by");

        ResultActions result = callOperation(operation);

        // 业务拒绝一律 HTTP 200（11.3.4）——本表里的码全是 200 系
        result.andExpect(status().isOk());

        if (expectedCode == 0) {
            result.andExpect(jsonPath("$.code").value(0));
            assertOperationEffect(operation, goodsId, queuedCount);
        } else {
            result.andExpect(jsonPath("$.code").value(expectedCode));
            assertNothingChanged(caseName, goodsId, status, freezeByBefore, queuedCount, tradingCount);
        }
    }

    @Test
    @DisplayName("I11-09 下架成功会归档：历史表逐列正确、当前表清空、且【不产生任何交易流水】")
    void offlineArchivesGoodsAndIntentions() throws Exception {
        String goodsId = insertGoods("on_sale", null, "待下架商品", "66.60");
        // 不写成 (LocalDateTime) 强转：JDBC 对 datetime 的默认映射类型是驱动的实现细节，
        // 这里只需「同一个访问器取到的值前后一致」，类型无关紧要。
        Object createAtBefore = goodsRow(goodsId).get("create_at");

        String succeededId = insertIntention(goodsId, "failed");
        jdbcTemplate.update("update simpleshop_intentions set fail_type = ? where id = ?", "voided", succeededId);
        String revokedId = insertIntention(goodsId, "revoked");

        mockMvc.perform(authorizedPost(SellerProductController.BASE_PATH + "/offline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$['data']").value(nullValue()));

        // ① 当前表清空（硬删除，不是软删除：DEC-04）
        assertThat(countGoods()).isZero();
        assertThat(count("select count(*) from simpleshop_intentions")).isZero();

        // ② 历史商品逐列核对
        Map<String, Object> history = jdbcTemplate.queryForMap(
                "select * from simpleshop_goods_history where id = ?", goodsId);
        assertThat(history.get("status")).as("归档恒写 off_sale（9-S 例外①）").isEqualTo("off_sale");
        assertThat(history.get("freeze_by")).as("归档恒清空（9-S 例外②）").isNull();
        assertThat(history.get("result")).as("手动下架 = offline").isEqualTo("offline");
        assertThat(history.get("trade_end")).as("trade_end 必填").isNotNull();
        assertThat(history.get("create_at"))
                .as("时间列【继承原值】，不得被回调重写")
                .isEqualTo(createAtBefore);
        assertThat(history.get("name")).isEqualTo("待下架商品");

        // ③ 历史意向：id 沿用、外键指向历史商品、且不含 queue_order/token 的数据
        List<Map<String, Object>> intentionHistory = jdbcTemplate.queryForList(
                "select * from simpleshop_intentions_history where fk_goods_history_id = ?", goodsId);
        assertThat(intentionHistory).hasSize(2);
        assertThat(intentionHistory)
                .extracting(row -> row.get("id"))
                .containsExactlyInAnyOrder(succeededId, revokedId);
        assertThat(intentionHistory)
                .extracting(row -> row.get("status"))
                .containsExactlyInAnyOrder("failed", "revoked");
        assertThat(intentionHistory.get(0))
                .as("历史意向表没有 queue_order/token 两列（DEC-DB-05、9-S 例外③）")
                .doesNotContainKeys("queue_order", "token");

        // ④ 手动下架不产生任何流水（§9.9.5）
        assertThat(count("select count(*) from simpleshop_trade_history"))
                .as("没有任何意向进入过交易，故流水必须为 0")
                .isZero();
    }

    @Test
    @DisplayName("I11-09 被拒时不产生【部分归档】：INV-05 失败后历史表必须为空")
    void rejectedOfflineLeavesNoPartialArchive() throws Exception {
        String goodsId = insertGoods("on_sale", null, "有 trading 意向的商品", "10.00");
        insertIntention(goodsId, "trading");

        mockMvc.perform(authorizedPost(SellerProductController.BASE_PATH + "/offline"))
                .andExpect(jsonPath("$.code").value(30005));

        // 归档的 INV-05 校验是【第一步】；若有人把它挪到写历史之后，
        // 事务虽会回滚，但这条断言能立刻暴露「先写后校验」的坏顺序（并验证回滚本身生效）
        assertThat(count("select count(*) from simpleshop_goods_history")).isZero();
        assertThat(count("select count(*) from simpleshop_intentions_history")).isZero();
        assertThat(goodsRow(goodsId).get("status")).isEqualTo("on_sale");
    }

    @Test
    @DisplayName("I11-07 冻结不碰意向（INV-07 精神：商品状态变化不影响已有意向）")
    void freezeDoesNotTouchIntentions() throws Exception {
        String goodsId = insertGoods("on_sale", null, "有排队商品", "10.00");
        String first = insertIntention(goodsId, "queued");
        String second = insertIntention(goodsId, "queued");

        mockMvc.perform(authorizedPost(SellerProductController.BASE_PATH + "/freeze"))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(jdbcTemplate.queryForList(
                "select id from simpleshop_intentions order by queue_order"))
                .extracting(row -> row.get("id"))
                .containsExactly(first, second);
        assertThat(jdbcTemplate.queryForList(
                "select status from simpleshop_intentions"))
                .extracting(row -> row.get("status"))
                .containsOnly("queued");
    }

    @Test
    @DisplayName("I11-09 生命周期：发布 → 下架 → 可再次发布（当前表已空，INV-01 不再拦截）")
    void afterOfflineANewGoodsCanBePublished() throws Exception {
        String firstId = publish("第一件", null, null, "10.00");
        mockMvc.perform(authorizedPost(SellerProductController.BASE_PATH + "/offline"))
                .andExpect(jsonPath("$.code").value(0));

        String secondId = publish("第二件", null, null, "20.00");

        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(countGoods()).isEqualTo(1);
        assertThat(count("select count(*) from simpleshop_goods_history")).isEqualTo(1);
    }

    @Test
    @DisplayName("I11-08 解冻后商品恢复在售，且 freeze_by 被清空")
    void unfreezeClearsFreezeBy() throws Exception {
        String id = publish("商品", null, null, "10.00");
        mockMvc.perform(authorizedPost(SellerProductController.BASE_PATH + "/freeze"))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(goodsRow(id).get("freeze_by")).isEqualTo("manual");

        mockMvc.perform(authorizedPost(SellerProductController.BASE_PATH + "/unfreeze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("on_sale"))
                .andExpect(jsonPath("$['data']['freeze_by']").value(nullValue()));

        assertThat(goodsRow(id).get("status")).isEqualTo("on_sale");
        assertThat(goodsRow(id).get("freeze_by")).isNull();
    }

    // =========================================================================
    // NFR-12 操作日志
    // =========================================================================

    @Test
    @DisplayName("NFR-12：发布/冻结/解冻/下架各留一条操作日志，含目标 ID 与操作者")
    void everyGoodsOperationIsAudited() throws Exception {
        try (AuditLogCapture audit = AuditLogCapture.audit()) {
            String id = publish("被审计的商品", null, null, "10.00");
            mockMvc.perform(authorizedPost(SellerProductController.BASE_PATH + "/freeze"))
                    .andExpect(jsonPath("$.code").value(0));
            mockMvc.perform(authorizedPost(SellerProductController.BASE_PATH + "/unfreeze"))
                    .andExpect(jsonPath("$.code").value(0));
            mockMvc.perform(authorizedPost(SellerProductController.BASE_PATH + "/offline"))
                    .andExpect(jsonPath("$.code").value(0));

            assertThat(audit.messages())
                    .as("发布 + 冻结 + 解冻 + 手动下架 = 4 条")
                    .hasSize(4);
            assertThat(audit.messages())
                    .extracting(message -> message.replaceAll(".*operation=(\\w+).*", "$1"))
                    .containsExactly(
                            OperationLogService.PUBLISH_GOODS,
                            OperationLogService.FREEZE_GOODS,
                            OperationLogService.UNFREEZE_GOODS,
                            OperationLogService.OFFLINE_GOODS);
            assertThat(audit.joined())
                    .as("每条都要能定位到目标对象与操作者（NFR-12 的可测指标②）")
                    .contains("targetType=" + OperationLogService.TARGET_GOODS)
                    .contains("targetId=" + id)
                    .contains("operator=" + ACCOUNT);
        }
    }

    @Test
    @DisplayName("NFR-12：被拒的操作【不】留操作日志（日志记的是「发生了什么」，不是「尝试了什么」）")
    void rejectedOperationsAreNotAudited() throws Exception {
        insertGoods("frozen", "manual", "已冻结商品", "10.00");

        try (AuditLogCapture audit = AuditLogCapture.audit()) {
            // 对已冻结商品再冻结 → 20003
            mockMvc.perform(authorizedPost(SellerProductController.BASE_PATH + "/freeze"))
                    .andExpect(jsonPath("$.code").value(20003));
            // 交易冻结不可解冻 → 这里 freeze_by=manual 且非在售，offline → 20003
            mockMvc.perform(authorizedPost(SellerProductController.BASE_PATH + "/offline"))
                    .andExpect(jsonPath("$.code").value(20003));

            assertThat(audit.messages())
                    .as("状态迁移被拒时不应写审计日志，否则「8 类操作各有记录」会被噪音淹没")
                    .isEmpty();
        }
    }

    // =========================================================================
    // 用例辅助：夹具
    // =========================================================================

    /** 构造带 token 的 GET 请求（<b>只构造、不发送</b>——发送统一由调用处的 {@code mockMvc.perform} 完成）。 */
    private MockHttpServletRequestBuilder authorizedGet(String path) {
        return get(path).header("Authorization", "Bearer " + token);
    }

    private MockHttpServletRequestBuilder authorizedPost(String path, String jsonBody) {
        return post(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody);
    }

    private MockHttpServletRequestBuilder authorizedPost(String path) {
        return post(path).header("Authorization", "Bearer " + token);
    }

    /**
     * 用 Jackson 构造发布请求体，而不是字符串拼接。
     *
     * <p>拼接迟早会在「值里含引号/反斜杠/换行」时产出非法 JSON——用例表里
     * {@code a{LF}b} 这样的输入正是这种情况。交给 Jackson 转义，测试才在测
     * <b>服务端校验</b>，而不是在测「测试自己能不能拼出合法 JSON」。
     * {@code null} 值会写成 JSON {@code null}（而不是省略键），从而命中 {@code @NotBlank}。
     */
    private String publishBody(String name, String description, String picUrl, String price) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", CsvValues.expand(name));
        body.put("description", CsvValues.expand(description));
        body.put("pic_url", CsvValues.expand(picUrl));
        body.put("price", CsvValues.expand(price));
        return objectMapper.writeValueAsString(body);
    }

    /** 走 {@code I11-06} 发布并返回新商品 ID。 */
    private String publish(String name, String description, String picUrl, String price) throws Exception {
        String body = mockMvc.perform(authorizedPost(SellerProductController.BASE_PATH,
                        publishBody(name, description, picUrl, price)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.data.id");
    }

    private ResultActions callOperation(String operation) throws Exception {
        String path = switch (operation) {
            case "freeze" -> SellerProductController.BASE_PATH + "/freeze";
            case "unfreeze" -> SellerProductController.BASE_PATH + "/unfreeze";
            case "offline" -> SellerProductController.BASE_PATH + "/offline";
            default -> throw new IllegalArgumentException("用例表的 operation 非法: " + operation);
        };
        return mockMvc.perform(authorizedPost(path));
    }

    /** 直接插一行商品（用于构造 {@code I11-06} 与正常流程都到不了的状态）。 */
    private String insertGoods(String status, String freezeBy, String name, String price) {
        String id = "GTEST" + String.format("%04d", SEQUENCE.getAndIncrement());
        LocalDateTime now = DatabaseTimeProvider.utcNow();
        jdbcTemplate.update("insert into simpleshop_goods "
                        + "(id,name,description,pic_url,price,status,freeze_by,create_at,update_at,"
                        + "trade_start,trade_end,result) "
                        + "values (?,?,?,?,?,?,?,?,?,NULL,NULL,NULL)",
                id, name, null, null, new java.math.BigDecimal(price), status, freezeBy, now, now);
        return id;
    }

    /** 直接插一条意向。{@code queue_order} 与 {@code token} 都有全局唯一约束，故用计数器分配。 */
    private String insertIntention(String goodsId, String status) {
        int sequence = SEQUENCE.getAndIncrement();
        String id = "ITEST" + String.format("%04d", sequence);
        // token 列宽 varchar(20)，契约形态是 12 位大写字母+数字
        String token = "T" + String.format("%011d", sequence);
        jdbcTemplate.update("insert into simpleshop_intentions "
                        + "(id,fk_good_id,queue_order,create_at,name,tel,status,fail_type,fail_reason,token) "
                        + "values (?,?,?,?,?,?,?,NULL,NULL,?)",
                id, goodsId, sequence, DatabaseTimeProvider.utcNow(), "GDS-买家", "13800000000", status, token);
        return id;
    }

    private Map<String, Object> goodsRow(String id) {
        return jdbcTemplate.queryForMap("select * from simpleshop_goods where id = ?", id);
    }

    private int countGoods() {
        return count("select count(*) from simpleshop_goods");
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    // =========================================================================
    // 用例辅助：断言
    // =========================================================================

    /** 操作成功后的状态断言（逐操作不同）。 */
    private void assertOperationEffect(String operation, String goodsId, int queuedCount) {
        switch (operation) {
            case "freeze" -> {
                assertThat(goodsRow(goodsId).get("status")).isEqualTo("frozen");
                assertThat(goodsRow(goodsId).get("freeze_by")).isEqualTo("manual");
                // PS-02：队列非空也能冻结，且排队的人不被清掉
                assertThat(count("select count(*) from simpleshop_intentions"))
                        .isEqualTo(queuedCount);
            }
            case "unfreeze" -> {
                assertThat(goodsRow(goodsId).get("status")).isEqualTo("on_sale");
                assertThat(goodsRow(goodsId).get("freeze_by")).isNull();
            }
            case "offline" -> {
                assertThat(countGoods()).isZero();
                assertThat(count("select count(*) from simpleshop_goods_history")).isEqualTo(1);
            }
            default -> throw new IllegalArgumentException("未处理的操作: " + operation);
        }
    }

    /**
     * 操作被拒后的「什么都没变」断言。
     *
     * <p>比「返回码对」更强：被拒的事务必须<b>完全回滚</b>——商品状态/冻结来源不变、
     * 意向条数与状态不变、且<b>历史表必须为空</b>（不得留下部分归档）。
     */
    private void assertNothingChanged(String caseName, String goodsId, String status,
                                      Object freezeByBefore, int queuedCount, int tradingCount) {
        if (goodsId == null) {
            assertThat(countGoods()).as("用例「%s」：无商品时不应凭空产生商品", caseName).isZero();
        } else {
            Map<String, Object> goods = goodsRow(goodsId);
            assertThat(goods.get("status")).as("用例「%s」：商品状态不得被改", caseName).isEqualTo(status);
            assertThat(goods.get("freeze_by")).as("用例「%s」：冻结来源不得被改", caseName)
                    .isEqualTo(freezeByBefore);
        }
        assertThat(count("select count(*) from simpleshop_intentions"))
                .as("用例「%s」：意向条数不得变化", caseName)
                .isEqualTo(queuedCount + tradingCount);
        assertThat(count("select count(*) from simpleshop_goods_history"))
                .as("用例「%s」：被拒的操作不得留下历史商品（无部分归档）", caseName)
                .isZero();
        assertThat(count("select count(*) from simpleshop_intentions_history"))
                .as("用例「%s」：被拒的操作不得留下历史意向（无部分归档）", caseName)
                .isZero();
    }

    // =========================================================================
    // 用例辅助：图片
    // =========================================================================

    private MockHttpServletRequestBuilder upload(String filename, String declaredContentType,
                                                byte[] content) {
        // ⚠️ 返回类型用父类 MockHttpServletRequestBuilder：header(...) 是父类方法，
        //    返回的也是父类类型，声明成子类会编译不过。
        return multipart(IMAGE_PATH)
                .file(new MockMultipartFile("file", filename, declaredContentType, content))
                .header("Authorization", "Bearer " + token);
    }

    private String uploadAndGetPicUrl(String filename, String declaredContentType, byte[] content) throws Exception {
        String body = mockMvc.perform(upload(filename, declaredContentType, content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.data.pic_url");
    }

    private String uploadAndGetPicUrl(String resourceName, String declaredContentType) throws Exception {
        return uploadAndGetPicUrl(resourceName, declaredContentType, readResource("/images/" + resourceName));
    }

    /** 把 {@code pic_url} 还原成磁盘路径（用配置的目录，因此在 {@code application-test.yml} 覆盖后仍成立）。 */
    private Path fileFor(String picUrl) {
        Path root = properties.getImage().directoryPath();
        String prefix = properties.getImage().getUrlPrefix();
        assertThat(picUrl).startsWith(prefix + "/");
        return root.resolve(picUrl.substring(prefix.length() + 1));
    }

    /** 造一个「合法 JPEG 魔数 + 填充」的载荷，长度精确为 {@code length}。 */
    private static byte[] jpegPaddedTo(int length) {
        byte[] content = new byte[length];
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;
        return content;
    }

    private static byte[] readResource(String path) {
        try (InputStream in = SellerGoodsApiTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("测试资源不存在: " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("读取测试资源失败: " + path, e);
        }
    }

    // =========================================================================
    // 用例辅助：清理
    // =========================================================================

    /**
     * 清空商品域的全部表（按外键逆序）。
     *
     * <p>{@code simpleshop_trade_history} 没有物理外键（{@code 9-N}），但先删它最省心。
     * 刻意<b>不动</b> {@code simpleshop_users}（种子账号）与 {@code simpleshop_queue_sequence} 的行，
     * 后者只把 {@code current_value} 复位，让各用例的 {@code queue_order} 互不影响。
     */
    private void cleanGoodsState() {
        jdbcTemplate.update("delete from simpleshop_trade_history");
        jdbcTemplate.update("delete from simpleshop_intentions_history");
        jdbcTemplate.update("delete from simpleshop_intentions");
        jdbcTemplate.update("delete from simpleshop_goods_history");
        jdbcTemplate.update("delete from simpleshop_goods");
        jdbcTemplate.update("update simpleshop_queue_sequence set current_value = 0 where id = 1");
    }

    /**
     * 删除测试图片目录。
     *
     * <p>「只允许删 test-images、否则失败」的护栏收在 {@link TestImageDirectory} 里
     * ——因为真实端口的上传用例也要用同一份判断，两处不能各写一遍。
     */
    private void deleteTestImageDirectory() {
        TestImageDirectory.delete(properties);
    }
}
