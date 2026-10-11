package com.simpleshop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import com.simpleshop.service.OperationLogService;
import com.simpleshop.session.SessionStore;
import com.simpleshop.testing.AuditLogCapture;

/**
 * 认证与会话的接口契约验证（{@code I11-01}、{@code I11-02}、{@code I11-03}）——S3 的验收凭据。
 *
 * <p>对应用户给定的 S3 完成标志：<b>「登录拿到 token；带 token 能访问；改密后旧 token 失效」</b>，
 * 并覆盖方案 §8.3「会话与鉴权」小节的全部条目、§12 的第 2／8／13 项验收口径。
 *
 * <h2>为什么跑真库（而不是 Mock 掉 Repository）</h2>
 * <p>本类里有一批断言<b>只有真库才能证伪</b>：
 * <ul>
 *   <li>{@code changePassword} 依赖「事务内托管实体的脏检查」把新哈希写回
 *       （{@code SellerAuthService} 刻意没有显式 {@code save}）。若 {@code @Transactional}
 *       哪天被去掉，实体变成游离态，{@code setPassword} 会<b>静默丢失</b>——
 *       Mock 掉仓储时这条永远不会失败。</li>
 *   <li>登录要真的走一遍 {@code findByAccount} + BCrypt 匹配，才能证明
 *       「账号不存在」与「口令错误」确实不可区分。</li>
 * </ul>
 * <p>数据源由 {@code @ActiveProfiles("test")} 指向 {@code simple_shop_test}，
 * <b>不触碰开发库</b>（见 {@code application-test.yml}）。
 *
 * <h2>⚠️ 本类会修改【种子账号】的口令，因此必须还原</h2>
 * <p>{@code I11-03} 的用例要真的改一次密码才能验证「全部会话失效」。而种子账号
 * {@code seller} 是环境的一部分（{@code V2__seed_initial_data.sql}），
 * {@code ArchiveAndUtcTest.seededSellerHashMatchesKnownPassword} 会断言
 * 它的哈希与 {@code Abcd@1234} 匹配。
 * <p>若不还原，那条既有用例的成败就取决于<b>测试类的执行顺序</b>——
 * 这是一种极难排查的假失败（单独跑绿、全量跑红）。因此：
 * <b>字节级捕获原始 {@code password} 与 {@code update_at}，在 {@code @AfterEach} 中原样写回</b>，
 * 并清空本用例创建的全部会话（{@code SessionStore} 是上下文单例，会跨用例残留）。
 */
@SpringBootTest
@ActiveProfiles("test")
class SellerAuthApiTest {

    /** 种子账号（{@code V2__seed_initial_data.sql}，{@code FR-01} 单账号）。 */
    private static final String ACCOUNT = "seller";

    /** 种子口令（README「初始账号与改密」）。 */
    private static final String SEED_PASSWORD = "Abcd@1234";

    /** 改密用例使用的新口令（≥8 位，满足 {@code C-17}）。 */
    private static final String NEW_PASSWORD = "Xk7#pQ2mW";

    private static final String WRONG_PASSWORD = "definitely-not-the-password";

    /** 契约要求的时间形状（方案 §8.3 断言 #3）。 */
    private static final String CONTRACT_TIME_SHAPE = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z";

    /** 未被拦截的卖家接口，用于把「有没有通过鉴权」变成一个状态码。 */
    private static final String PROBE_PATH = "/api/seller/product";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    /** 种子账号的原始口令哈希与 {@code update_at}，用于 {@code @AfterEach} 原样还原。 */
    private String seedPasswordHash;
    private LocalDateTime seedUpdateAt;

    @BeforeEach
    void rememberSeedAccountThenClearSessions() {
        // MockMvc 只建一次：NFR-03 的用例要发 100 个请求，逐个重建没必要
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        seedPasswordHash = jdbcTemplate.queryForObject(
                "select password from simpleshop_users where account = ?", String.class, ACCOUNT);
        seedUpdateAt = jdbcTemplate.queryForObject(
                "select update_at from simpleshop_users where account = ?", LocalDateTime.class, ACCOUNT);
        assertThat(seedPasswordHash).as("V2 种子账号 seller 必须存在").isNotBlank();

        // SessionStore 是 Spring 上下文单例，会跨测试类残留会话（如 ApiContractTest 也建过会话）；
        // 每个用例从「无会话」开始，断言才不依赖执行顺序。
        sessionStore.removeAllFor(ACCOUNT);
    }

    @AfterEach
    void restoreSeedAccountAndSessions() {
        if (seedPasswordHash != null) {
            jdbcTemplate.update(
                    "update simpleshop_users set password = ?, update_at = ? where account = ?",
                    seedPasswordHash, seedUpdateAt, ACCOUNT);
        }
        sessionStore.removeAllFor(ACCOUNT);
    }

    // =========================================================================
    // I11-01 登录
    // =========================================================================

    @Test
    @DisplayName("I11-01 登录成功：HTTP 200 + code 0 + data{token, expire_at}")
    void loginSucceeds() throws Exception {
        MvcResult result = login(ACCOUNT, SEED_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();

        // expire_at 必须是契约形状（带 Z、精确到秒）
        String expireAt = JsonPath.read(body(result), "$.data.expire_at");
        assertThat(expireAt).matches(CONTRACT_TIME_SHAPE);
    }

    @Test
    @DisplayName("I11-01 token 形态：URL-safe Base64，约 43 字符（32 随机字节，无填充）")
    void loginTokenHasExpectedShape() throws Exception {
        String token = loginAndGetToken();

        assertThat(token)
                .as("SecureRandom 32 字节 → URL-safe Base64 无填充 = 43 字符")
                .hasSize(43)
                .matches("[A-Za-z0-9_-]+");
        assertThat(token)
                .as("token 必须是随机会话 id，不能是可推断的账号或时间戳")
                .doesNotContain(ACCOUNT);
    }

    @Test
    @DisplayName("I11-01 expire_at 约为 30 分钟后（C-07、DEC-13：30 分钟无操作超时）")
    void loginExpiryIsThirtyMinutesAhead() throws Exception {
        // expire_at 精确到秒（小数截断），故允许几秒误差
        Instant expireAt = Instant.parse(JsonPath.read(body(login(ACCOUNT, SEED_PASSWORD).andReturn()),
                "$.data.expire_at"));

        long seconds = Duration.between(Instant.now(), expireAt).toSeconds();

        assertThat(seconds)
                .as("会话有效期必须是 30 分钟量级——既不是 30 秒也不是 30 小时")
                .isBetween(1_700L, 1_800L);
    }

    @Test
    @DisplayName("I11-01 登录成功后，该 token 可以访问卖家接口")
    void loginTokenGrantsAccess() throws Exception {
        String token = loginAndGetToken();

        // ⚠️ 此刻【不能】断言 200：I11-04（GET /api/seller/product）要到 S4 才实现，
        //    当前命中「无此路由」→ 404。本用例要证明的是「【没有】被鉴权拒绝」（不是 401），
        //    这个断言在 S4 实现该接口后依然成立（届时会变成 200）。
        assertThat(statusOfGet(PROBE_PATH, token))
                .as("携带登录得到的 token 不应再被鉴权拒绝")
                .isNotEqualTo(401);
    }

    @Test
    @DisplayName("I11-01 口令错误：HTTP 200 + code 10001（不是 401、不是 400）")
    void loginWithWrongPasswordReturns10001OverHttp200() throws Exception {
        // ⚠️ 这里最容易做错：把「账号密码错误」当成 401 Unauthorized。
        //    契约 §11.3.4 明确 401 【只】用于「未携带 token / token 无效 / 会话超时」，
        //    而登录失败属于「业务规则拒绝」→ 200 + 业务码。前端据此在 code 分支提示 M10-23。
        login(ACCOUNT, WRONG_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.message").value("invalid account or password"))
                .andExpect(jsonPath("$['data']").value(nullValue()));
    }

    @Test
    @DisplayName("I11-01 10001 统一提示：账号不存在与口令错误【完全不可区分】（M10-23）")
    void unknownAccountIsIndistinguishableFromWrongPassword() throws Exception {
        MvcResult wrongPassword = login(ACCOUNT, WRONG_PASSWORD).andReturn();
        MvcResult unknownAccount = login("no-such-account-at-all", WRONG_PASSWORD).andReturn();

        // 逐字节比对整个响应体（含 code、message、data），这是「不区分」最强的表达：
        // 任何「给账号不存在换个码/换句话/带上提示」的改动都会让本用例失败。
        assertThat(unknownAccount.getResponse().getStatus())
                .isEqualTo(wrongPassword.getResponse().getStatus())
                .isEqualTo(200);
        assertThat(body(unknownAccount))
                .as("账号不存在与口令错误的响应体必须逐字节相同")
                .isEqualTo(body(wrongPassword));
    }

    @Test
    @DisplayName("I11-01 登录失败后不建立会话（不能凭失败响应拿到可用 token）")
    void failedLoginCreatesNoSession() throws Exception {
        int before = sessionStore.activeCount();

        login(ACCOUNT, WRONG_PASSWORD).andExpect(jsonPath("$.code").value(10001));

        assertThat(sessionStore.activeCount())
                .as("被拒的登录不得留下会话记录")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("I11-01 参数缺失/空白 → HTTP 400 + code 50002")
    void loginWithMissingCredentialsIsParamInvalid() throws Exception {
        // 账号空白
        login("", SEED_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50002));
        // 口令空白
        login(ACCOUNT, "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50002));
        // 两个字段都缺（键不存在）
        rawLogin("{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50002));
        // 账号超长（>50，与列宽一致）
        login("a".repeat(51), SEED_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50002));
    }

    @Test
    @DisplayName("I11-01 报文非法（JSON 语法错误 / 多余字段）→ HTTP 400 + code 50002")
    void loginWithMalformedBodyIsParamInvalid() throws Exception {
        // JSON 语法错误
        mockMvc.perform(post(WebMvcConfig.SESSION_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"seller\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50002));

        // 契约未定义的字段：JacksonConfig 刻意保留 FAIL_ON_UNKNOWN_PROPERTIES，
        // 让「前端多传了一个字段」当场暴露，而不是被静默忽略（方案 §2.2）
        mockMvc.perform(post(WebMvcConfig.SESSION_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"seller\",\"password\":\"x\",\"rememberMe\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50002));
    }

    @Test
    @DisplayName("I11-01 错误的 Content-Type → HTTP 400 + code 50002（而不是 500）")
    void loginWithWrongContentTypeIsParamInvalidNotInternalError() throws Exception {
        // 这是联调时最常见的错误之一。Spring 在进入 Controller 之前就抛
        // HttpMediaTypeNotSupportedException；若不显式处理会落到兜底分支变成
        // 500 + 50000，把「客户端写错了」误报成「服务端内部错误」，污染 500 告警。
        mockMvc.perform(post(WebMvcConfig.SESSION_PATH)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"account\":\"seller\",\"password\":\"Abcd@1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50002));
    }

    @Test
    @DisplayName("NFR-03：口令连续错误 100 次后仍可正常登录（不存在任何锁定机制）")
    void noLockoutAfterHundredFailedAttempts() throws Exception {
        // DEC-25 明确「不做登录失败次数限制」，NFR-03 把它变成可测指标。
        // 之所以真的打满 100 次：任何「第 N 次锁定」的实现都会在这条用例上露出
        // （返回码变化、或第 101 次用正确口令也登不进去）。
        for (int attempt = 1; attempt <= 100; attempt++) {
            login(ACCOUNT, WRONG_PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10001));
        }

        login(ACCOUNT, SEED_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // =========================================================================
    // I11-02 退出登录
    // =========================================================================

    @Test
    @DisplayName("I11-02 退出登录：HTTP 200 + code 0 + data null，且原 token 立即失效")
    void logoutRevokesToken() throws Exception {
        String token = loginAndGetToken();
        assertThat(statusOfGet(PROBE_PATH, token)).isNotEqualTo(401);

        mockMvc.perform(delete(WebMvcConfig.SESSION_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // 契约：无数据时 data 为 null，但【键必须存在】
                .andExpect(jsonPath("$['data']").value(nullValue()))
                .andExpect(content().string(containsString("\"data\":null")));

        assertThat(statusOfGet(PROBE_PATH, token))
                .as("退出登录后原 token 必须立即失效")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("I11-02 幂等：不带 token / 无效 token 一律 HTTP 200 + code 0（绝不 401）")
    void logoutIsIdempotentAndNeverReturns401() throws Exception {
        // 口径见 WebMvcConfig#addInterceptors：I11-01 与 I11-02 共用路径，
        // excludePathPatterns 按【路径】排除 ⇒ 退出登录不经拦截器，拿不到「已认证账号」。
        // 因此它必须自己解析 token，并保证「无效 token」也是成功——
        // 否则前端在 token 已过期时调退出接口会先收到 401 并跳登录页，
        // 而这一步本应静默完成（用户本来就是想退出）。
        mockMvc.perform(delete(WebMvcConfig.SESSION_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$['data']").value(nullValue()));

        mockMvc.perform(delete(WebMvcConfig.SESSION_PATH).header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(delete(WebMvcConfig.SESSION_PATH).header("Authorization", "no-scheme-at-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("I11-02 只失效本条会话，不影响同一账号的其它会话")
    void logoutOnlyRevokesItsOwnSession() throws Exception {
        String tokenA = loginAndGetToken();
        String tokenB = loginAndGetToken();

        mockMvc.perform(delete(WebMvcConfig.SESSION_PATH).header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(statusOfGet(PROBE_PATH, tokenA)).isEqualTo(401);
        assertThat(statusOfGet(PROBE_PATH, tokenB))
                .as("退出登录是「这条会话」的操作，不是「这个账号」的操作——"
                        + "后者是改密（DEC-17）的语义，两者不可混")
                .isNotEqualTo(401);
    }

    // =========================================================================
    // I11-03 修改密码
    // =========================================================================

    @Test
    @DisplayName("I11-03 改密成功：HTTP 200 + code 0；新口令可登录、旧口令被拒")
    void changePasswordUpdatesHash() throws Exception {
        String token = loginAndGetToken();

        changePassword(token, SEED_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$['data']").value(nullValue()));

        // ⚠️ 这条断言是「Service 依赖托管实体脏检查写库」的护栏：
        //    SellerAuthService 刻意没有显式 save()。若 @Transactional 丢失，
        //    setPassword 会静默丢失，下面这次用新口令的登录就会拿到 10001。
        login(ACCOUNT, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        login(ACCOUNT, SEED_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001));
    }

    @Test
    @DisplayName("I11-03 改密后【全部】会话立即失效（DEC-17、NFR-04、AC-27 的双会话对照）")
    void changePasswordRevokesEverySessionIncludingCallers() throws Exception {
        // 造两条会话：A（发起改密）与 B（旁观者）。
        // 契约要求 B 也必须失效——只失效 A 是常见但错误的实现（只删当前 token）。
        String tokenA = loginAndGetToken();
        String tokenB = loginAndGetToken();
        assertThat(sessionStore.activeCount()).as("两条会话都应已建立").isGreaterThanOrEqualTo(2);

        changePassword(tokenA, SEED_PASSWORD, NEW_PASSWORD)
                .andExpect(jsonPath("$.code").value(0));

        assertThat(statusOfGet(PROBE_PATH, tokenA))
                .as("发起改密的会话本身也必须失效（契约 I11-03 明确「含当前 token」→ 前端跳 P10-05）")
                .isEqualTo(401);
        assertThat(statusOfGet(PROBE_PATH, tokenB))
                .as("其它会话必须一并失效（DEC-17；只失效当前 token 是错误实现）")
                .isEqualTo(401);

        assertThat(sessionStore.activeCount())
                .as("该账号不应残留任何会话")
                .isZero();
    }

    @Test
    @DisplayName("I11-03 原密码不正确 → HTTP 200 + code 10003，且【不】失效任何会话、不改口令")
    void changePasswordWithWrongOldPasswordRevokesNothing() throws Exception {
        String tokenA = loginAndGetToken();
        String tokenB = loginAndGetToken();

        changePassword(tokenA, WRONG_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));

        // ⚠️ 这条用例的价值在于证明「被拒的改密没有副作用」：
        //    最危险的错误实现是「先清会话、后校验原密码」，那样一次填错原密码
        //    就会把用户踢下线，而且口令根本没改。
        assertThat(statusOfGet(PROBE_PATH, tokenA))
                .as("改密被拒不得失效会话")
                .isNotEqualTo(401);
        assertThat(statusOfGet(PROBE_PATH, tokenB)).isNotEqualTo(401);

        // 口令未变：旧口令仍可登录，新口令不可
        login(ACCOUNT, SEED_PASSWORD).andExpect(jsonPath("$.code").value(0));
        login(ACCOUNT, NEW_PASSWORD).andExpect(jsonPath("$.code").value(10001));
    }

    @Test
    @DisplayName("I11-03 新密码不足 8 位 → HTTP 200 + code 10004（不是 400 + 50002）")
    void changePasswordWithShortNewPasswordReturns10004OverHttp200() throws Exception {
        String token = loginAndGetToken();

        // ⚠️ 这是「DTO 注解 vs 业务错误码」冲突的回归护栏（方案 §4.6.3 的修正）。
        //    若有人"顺手"在 ChangePasswordRequest.newPassword 上加回 @Size(min = 8)，
        //    请求会在进入 Controller 前被 Bean Validation 拦下 → 400 + 50002，
        //    契约指定的 10004（HTTP 200）就变成死码，前端按 code 映射的
        //    「新密码至少 8 位」就地提示永远不会出现。
        changePassword(token, SEED_PASSWORD, "Ab1@x")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10004));
    }

    @Test
    @DisplayName("I11-03 恰好 8 位的新密码是合法的（边界值：下界取闭区间）")
    void changePasswordAcceptsExactlyEightCharacters() throws Exception {
        String token = loginAndGetToken();

        // 「≥ 8」是 C-17 的原文，下界含 8 本身。差一错误（写成 > 8）会把 8 位密码
        // 也判成 10004，用户会看到一个「刚好 8 位却说太短」的提示。
        changePassword(token, SEED_PASSWORD, "Ab1@wx78")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("I11-03 参数缺失/空白 → HTTP 400 + code 50002")
    void changePasswordWithBlankFieldsIsParamInvalid() throws Exception {
        String token = loginAndGetToken();

        changePassword(token, "", NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50002));
        changePassword(token, SEED_PASSWORD, "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50002));

        mockMvc.perform(put(SellerAccountController.PASSWORD_PATH)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50002));
    }

    @Test
    @DisplayName("I11-03 未带 / 无效 token → HTTP 401 + code 10002（由拦截器拦下）")
    void changePasswordRequiresSession() throws Exception {
        // 改密路径【不】在拦截器的排除清单里，因此鉴权在工作：
        // 这也是「10002 由拦截器而非 Service 产生」的证据（Service 收的是已认证账号）。
        mockMvc.perform(put(SellerAccountController.PASSWORD_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"old_password\":\"" + SEED_PASSWORD + "\",\"new_password\":\""
                                + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002));

        changePassword("not-a-real-token", SEED_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002));

        // 鉴权被拒时不得到达 Service：口令必须没被改
        login(ACCOUNT, SEED_PASSWORD).andExpect(jsonPath("$.code").value(0));
    }

    // =========================================================================
    // NFR-12：操作日志（第 8 类「修改密码」）
    // =========================================================================

    @Test
    @DisplayName("NFR-12：改密写审计日志，含操作类型与操作者，且【不含任何口令值】")
    void changePasswordWritesAuditLogWithoutAnyPasswordValue() throws Exception {
        try (AuditLogCapture audit = AuditLogCapture.audit()) {
            String token = loginAndGetToken();

            changePassword(token, SEED_PASSWORD, NEW_PASSWORD).andExpect(jsonPath("$.code").value(0));

            assertThat(audit.messages())
                    .as("NFR-12 的 8 类操作清单里包含「修改密码」")
                    .anySatisfy(message -> assertThat(message)
                            .contains("operation=" + OperationLogService.CHANGE_PASSWORD));
            assertThat(audit.messages())
                    .as("操作者应来自拦截器写入的请求属性（证明 session→attribute→日志 这条链是通的）")
                    .anySatisfy(message -> assertThat(message).contains("operator=" + ACCOUNT));

            // ⚠️ 核心安全断言：日志里不得出现任何口令形态。
            //    这类泄漏一旦发生就会永久留在 3 个月留存期的审计文件里（C-18、12-P7）。
            String hashInDb = jdbcTemplate.queryForObject(
                    "select password from simpleshop_users where account = ?", String.class, ACCOUNT);

            assertThat(audit.joined())
                    .as("明文口令（旧/新）绝不能进日志")
                    .doesNotContain(SEED_PASSWORD)
                    .doesNotContain(NEW_PASSWORD);
            assertThat(audit.joined())
                    .as("BCrypt 哈希同样不能进日志（它是可直接用于爆破的素材）")
                    .doesNotContain(hashInDb)
                    .doesNotContain(seedPasswordHash);
        }
    }

    @Test
    @DisplayName("NFR-12：登录/退出【不】写审计日志（8 类关键操作不含会话生命周期事件）")
    void loginAndLogoutAreNotAudited() throws Exception {
        try (AuditLogCapture audit = AuditLogCapture.audit()) {
            String token = loginAndGetToken();
            mockMvc.perform(delete(WebMvcConfig.SESSION_PATH).header("Authorization", "Bearer " + token))
                    .andExpect(jsonPath("$.code").value(0));

            assertThat(audit.messages())
                    .as("登录与退出不是 NFR-12 的 8 类操作；把它们记进去会让"
                            + "「8 类各有记录」的验收口径变成含糊的「大约 8 类」")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("NFR-12：报文解析失败的日志【不】回显请求体（防止口令随解析错误进日志）")
    void malformedBodyLogDoesNotEchoRequestBody() throws Exception {
        // Jackson 的解析异常消息是从请求体原文生成的，会回显片段。
        // 实测：{"password":Abcd@1234}（值少了引号）会得到
        //   Unrecognized token 'Abcd': was expecting (JSON String, Number, ...)
        // 也就是说客户端只要把报文写坏，口令明文就可能落进服务端日志——
        // 而 GlobalExceptionHandler 的解析失败分支是【唯一】一处会把异常原始消息写进日志的地方。
        // 这里用一望即知的标记串来代替真实口令，断言它没有被回显。
        try (AuditLogCapture handlerLogs = AuditLogCapture.of(GlobalExceptionHandler.class)) {
            rawLogin("{\"account\":\"seller\",\"password\":LEAKCANARY9}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(50002));

            assertThat(handlerLogs.joined())
                    .as("解析失败的日志只应记「异常类别 + 位置」，不得回显请求体片段")
                    .doesNotContain("LEAKCANARY9");
            assertThat(handlerLogs.joined())
                    .as("但仍要留下可排查的信息：异常类别")
                    .contains("JsonParseException");
        }
    }

    @Test
    @DisplayName("报文解析失败的日志保留「未知字段名」（字段名是元数据，不是用户数据）")
    void malformedBodyLogKeepsUnknownPropertyName() throws Exception {
        try (AuditLogCapture handlerLogs = AuditLogCapture.of(GlobalExceptionHandler.class)) {
            rawLogin("{\"account\":\"seller\",\"password\":\"x\",\"rememberMe\":true}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(50002));

            // 脱敏不能把有用的信息一起丢掉：未知字段名是排查这类问题最需要的线索
            assertThat(handlerLogs.joined())
                    .contains("UnrecognizedPropertyException")
                    .contains("property=rememberMe");
        }
    }

    // =========================================================================
    // 辅助
    // =========================================================================

    private ResultActions login(String account, String password) throws Exception {
        return rawLogin("{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}");
    }

    private ResultActions rawLogin(String json) throws Exception {
        return mockMvc.perform(post(WebMvcConfig.SESSION_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /** 登录并返回 token（前置失败时直接让用例失败，不返回 null 让后续断言莫名报错）。 */
    private String loginAndGetToken() throws Exception {
        MvcResult result = login(ACCOUNT, SEED_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return JsonPath.read(body(result), "$.data.token");
    }

    private ResultActions changePassword(String token, String oldPassword, String newPassword) throws Exception {
        MockHttpServletRequestBuilder builder = put(SellerAccountController.PASSWORD_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"old_password\":\"" + oldPassword + "\",\"new_password\":\"" + newPassword + "\"}");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(builder);
    }

    /** 用给定 token 探测一个受保护的卖家接口，返回 HTTP 状态码。 */
    private int statusOfGet(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }
}
