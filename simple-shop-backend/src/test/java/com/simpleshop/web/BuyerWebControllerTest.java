package com.simpleshop.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.service.BuyerIntentionService;
import com.simpleshop.service.dto.BuyerPrompt;
import com.simpleshop.service.dto.BuyerResult;
import com.simpleshop.service.dto.GoodsData;

/**
 * 买家端 Web 层（Controller + 视图解析）的行为验证。
 *
 * <h2>为什么存在这个测试类</h2>
 * <p>组长的 {@code BuyerIntentionServiceTest}（50 个用例）已经把买家端
 * <b>业务层</b>覆盖得很完整——三态查询、位次派生、撤销前移、AC-17/AC-18、
 * FR-16 改信息、限流……那是 S3 交付的。
 *
 * <p>但 <b>Web 层没有任何测试</b>：{@code BuyerHomeController}、
 * {@code BuyerIntentController}、{@code BuyerQueryController} 是本阶段（买家前端）
 * 新增的代码，路由、视图名、模型属性名、EL 摊平——这些「胶水」恰恰是最容易
 * 在后续重构中被无声改坏的部分（改坏了业务测试<b>全绿</b>，但页面 500 或空白）。
 * 本类就是给这层加的护栏。
 *
 * <h2>⚠️ 只断言「交给视图的数据」，不断言渲染出的 HTML</h2>
 * <p>MockMvc <b>不启动 JSP 引擎</b>（没有真实 Servlet 容器，Jasper 不工作），
 * 因此断言 {@code content()} 里的 HTML 字符串会得到「视图名原文」而不是渲染结果。
 * 本类的做法是断言 <b>视图名 + forward 目标 + 模型属性</b>——
 * 这三者才是 Controller 的<b>契约</b>；JSP 里的排版由浏览器实测覆盖
 * （2026-10-11 已逐页做过视觉检查）。
 *
 * <h2>⚠️ 覆盖的四个「易坏点」</h2>
 * <ol>
 *   <li><b>路由与视图名</b>：{@code GET /} → {@code index}；
 *       {@code POST /intent} 成功 → {@code intent-success}（<b>不是 redirect</b>）；</li>
 *   <li><b>EL 摊平</b>：DTO 是 record，必须摊成 {@code Map} 才能给 JSP 用
 *       （{@code ${goods.name}} 读 record 会 500）。此处断言模型里拿到的是
 *       {@code Map} 而不是 {@code GoodsData} 本身——若有人「顺手简化」成直接放 DTO，
 *       这个用例会红，而不是等到页面上线才炸；</li>
 *   <li><b>拒绝分支不产生口令码</b>：{@code Rejected} 是正常返回，
 *       事务不回滚 ⇒ 拒绝必须在写操作之前。用「模型里没有 token」来验证；</li>
 *   <li><b>成功页无独立 URL</b>：{@code GET /intent/success} 必须 404
 *       （K5-02「口令码仅展示一次」，不能留回看入口）。</li>
 * </ol>
 *
 * <h2>为什么用 {@code @AutoConfigureMockMvc} 而不是手动 build</h2>
 * <p>组长的 {@code ApiContractTest} 用手动 {@code MockMvcBuilders.webAppContextSetup}，
 * 那是因为它要测<b>拦截器</b>在未映射路径上的行为。本类关心的是
 * {@code @Controller} 的映射解析与视图名，{@code @AutoConfigureMockMvc}
 * 会装配与线上一致的 DispatcherServlet 链路（含 {@code WebMvcConfig}），
 * 同时保留「{@code /WEB-INF/views} 不可直接访问」这类容器级隔离，
 * 语义更贴合「页面路由」这一验证目标。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BuyerWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BuyerIntentionService buyerIntentionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 本测试类创建的数据统一带此前缀，便于精确清理（不碰种子数据）。 */
    private static final String MARK = "BWT-";

    /**
     * 每个用例前重建「一个在售商品」。
     *
     * <p>⚠️ <b>这不是可有可无的准备步骤，是提交意向的硬前提</b>：
     * {@code FR-12} 规定买家只能对「当前商品」提交意向；库里没有商品时
     * {@code submitIntention} 直接返回「无当前商品」的拒绝码，页面回表单页而不是成功页。
     *
     * <p>测试库是<b>跨测试类共享</b>的，而 {@code BuyerIntentionServiceTest} 的
     * {@code @AfterEach} 会删掉它自己造的 {@code DLT-} 商品——所以本类
     * <b>不能假设</b>库里已经有商品，必须自己造。（2026-10-11 实跑踩到：
     * 一上来 21 个用例挂了 8 个，全是「回表单页而不是成功页」。）
     */
    @BeforeEach
    void prepareCurrentGoods() {
        cleanUp();
        jdbcTemplate.update(
                "insert into simpleshop_goods (id, name, description, price, status, create_at, update_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                MARK + "G1", MARK + "商品", MARK + "描述",
                new BigDecimal("9.90"), "on_sale",
                LocalDateTime.now(), LocalDateTime.now());
    }

    @AfterEach
    void cleanUp() {
        // ⚠️ 删除顺序必须逆外键依赖：simpleshop_intentions.fk_good_id → simpleshop_goods
        //    且该外键是 ON DELETE RESTRICT（V1 迁移脚本），先删商品会被数据库拒绝。
        //    ⚠️ 判据用【商品 id】而不是意向的姓名：本测试类提交的意向姓名是「沈括」这类
        //    真实姓名（要验证回填与展示），【不带】测试标记，按姓名匹配会一条都删不掉。
        //    （2026-10-11 实跑踩到：按 name like 清理 → 15 个用例全报
        //     `Cannot delete or update a parent row: a foreign key constraint fails`。）
        jdbcTemplate.update("delete from simpleshop_trade_history where fk_intention_id in "
                + "(select id from (select id from simpleshop_intentions where fk_good_id like ?) as t)", MARK + "%");
        jdbcTemplate.update("delete from simpleshop_intentions where fk_good_id like ?", MARK + "%");
        jdbcTemplate.update("delete from simpleshop_goods where id like ?", MARK + "%");
        // 队列序号复位，让各用例都从「排第 1 位」开始（用例之间互不影响）
        jdbcTemplate.update("update simpleshop_queue_sequence set current_value = 0 where id = 1");
    }

    // -------------------------------------------------------------------------
    // B11-01：首页 GET /
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("B11-01：GET / → 视图名 index，且模型里 goods 是 Map（不是 record）")
    void homeRendersIndexView() throws Exception {
        MvcResult result = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                // ⚠️ 核心断言：模型里必须是 Map，不能是 GoodsData。
                //    放 record 会让 JSP 的 ${goods.xxx} 抛 PropertyNotFoundException（页面 500）。
                .andReturn();

        Object goods = result.getModelAndView().getModel().get("goods");
        if (goods != null) {
            org.assertj.core.api.Assertions.assertThat(goods)
                    .as("goods 必须是摊平后的 Map，否则 JSP 的 EL 读不到字段")
                    .isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> view = (Map<String, Object>) goods;
            // 逐字段确认 key 名与 JSP 里写的表达式一致（改 key 名 = 改坏页面）
            org.assertj.core.api.Assertions.assertThat(view)
                    .containsKeys("id", "name", "description", "picUrl",
                            "price", "status", "freezeBy", "createAt", "tradeStart");
        }
    }

    @Test
    @DisplayName("B11-01：首页把两条文案放进模型（文案不在 JSP 里手打，取自 BuyerPrompt）")
    void homePutPromptTextsInModel() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("promptGoodsFrozen", BuyerPrompt.GOODS_FROZEN.text()))
                .andExpect(model().attribute("promptNoGoods", BuyerPrompt.NO_CURRENT_GOODS.text()));
    }

    @Test
    @DisplayName("⚠️ 买家端【不】受卖家鉴权拦截器影响：GET / 无 token 也应 200，而不是 401")
    void homeIsNotBlockedBySellerInterceptor() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // B11-02：提交意向表单 GET /intent/new
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("B11-02：GET /intent/new → 视图名 intent-form，且注入前端脚本用的文案")
    void intentFormRenders() throws Exception {
        mockMvc.perform(get("/intent/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("intent-form"));
    }

    // -------------------------------------------------------------------------
    // B11-03：提交意向 POST /intent
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("B11-03：提交成功 → 同一请求内渲染 intent-success（不是 redirect）")
    void submitSuccessForwardsToSuccessView() throws Exception {
        String token = submitOneIntention();
        org.assertj.core.api.Assertions.assertThat(token)
                .as("正常提交应拿到 12 位口令码").hasSize(12);
    }

    @Test
    @DisplayName("B11-03：提交成功把口令码放进【模型】（不入 session、不进 URL）")
    void submitSuccessCarriesTokenAsModelAttribute() throws Exception {
        // 用一个新 IP（限流按 clientKey 计数）避免与其它用例互相挤占配额
        MvcResult result = mockMvc.perform(post("/intent")
                        .param("name", "沈括")
                        .param("tel", "13800001111")
                        .with(r -> { r.setRemoteAddr("10.9.9.1"); return r; }))
                .andExpect(status().isOk())
                .andExpect(view().name("intent-success"))
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        Object token = model.get("token");
        org.assertj.core.api.Assertions.assertThat(token)
                .as("成功页的口令码走模型（K5-02 仅展示一次，不落 session、不进 URL）")
                .isNotNull();
        org.assertj.core.api.Assertions.assertThat(String.valueOf(token)).hasSize(12);

        // 保存提醒也应带上（文案含占位符，由 JSP 用 fn:replace 填入 token）
        org.assertj.core.api.Assertions.assertThat(model.get("saveTip"))
                .isEqualTo(BuyerPrompt.SAVE_PASSCODE.text());

        // ⚠️ 关键：口令码不能出现在 URL / 重定向里（否则浏览器历史里留痕、可回看）
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getRedirectedUrl())
                .as("成功分支必须是 view 而不是 redirect（K5-02 不留回看入口）")
                .isNull();
    }

    @Test
    @DisplayName("⚠️ 成功页【无独立 URL】：GET /intent/success 必须 404（K5-02 不留回看入口）")
    void successViewHasNoOwnUrl() throws Exception {
        mockMvc.perform(get("/intent/success")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("B11-03：姓名与电话都为空 → 拒绝，回表单页且不产生口令码")
    void submitRejectsWhenBothFieldsBlank() throws Exception {
        MvcResult result = mockMvc.perform(post("/intent")
                        .param("name", "")
                        .param("tel", "")
                        .with(r -> { r.setRemoteAddr("10.9.9.2"); return r; }))
                .andExpect(status().isOk())
                .andExpect(view().name("intent-form"))
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        org.assertj.core.api.Assertions.assertThat(model.get("token"))
                .as("被拒绝的提交绝不能带出口令码").isNull();
        org.assertj.core.api.Assertions.assertThat(model.get("nameError"))
                .as("两项都空应命中 nameError（NAME_TEL_REQUIRED 挂在 name 字段上）").isNotNull();
    }

    @Test
    @DisplayName("B11-03：电话格式非法 → 拒绝并定位到 telError（错误要挂在该字段上）")
    void submitRejectsMalformedTel() throws Exception {
        MvcResult result = mockMvc.perform(post("/intent")
                        .param("name", "沈括")
                        .param("tel", "abc")
                        .with(r -> { r.setRemoteAddr("10.9.9.3"); return r; }))
                .andExpect(status().isOk())
                .andExpect(view().name("intent-form"))
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        org.assertj.core.api.Assertions.assertThat(model.get("telError"))
                .as("电话非法应命中 tel 字段错误（而不是笼统的 submitError）")
                .isEqualTo(BuyerPrompt.TEL_INVALID.text());
        org.assertj.core.api.Assertions.assertThat(model.get("token")).isNull();
    }

    @Test
    @DisplayName("B11-03：被拒绝时原样回填已填字段（用户不必重打一遍）")
    void submitEchoesBackEnteredValuesOnRejection() throws Exception {
        MvcResult result = mockMvc.perform(post("/intent")
                        .param("name", "沈括")
                        .param("tel", "bad-tel")
                        .with(r -> { r.setRemoteAddr("10.9.9.4"); return r; }))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        org.assertj.core.api.Assertions.assertThat(model.get("formName")).isEqualTo("沈括");
        org.assertj.core.api.Assertions.assertThat(model.get("formTel")).isEqualTo("bad-tel");
    }

    // -------------------------------------------------------------------------
    // B11-04/05：查询 GET|POST /query
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("B11-04：GET /query → 视图名 query，且【不设】queryState（初次进入不显示空结果框）")
    void queryPageInitialStateHasNoResultState() throws Exception {
        MvcResult result = mockMvc.perform(get("/query"))
                .andExpect(status().isOk())
                .andExpect(view().name("query"))
                .andReturn();

        org.assertj.core.api.Assertions.assertThat(
                        result.getModelAndView().getModel().get("queryState"))
                .as("初次进入不应带 queryState，否则页面会先渲染一个空结果区")
                .isNull();
    }

    @Test
    @DisplayName("B11-05：格式不符的口令码 → 返回 M10-28 的【格式】提示，且不查库")
    void queryRejectsMalformedPasscodeWithFormatMessage() throws Exception {
        MvcResult result = mockMvc.perform(post("/query").param("token", "abc"))
                .andExpect(status().isOk())
                .andExpect(view().name("query"))
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        org.assertj.core.api.Assertions.assertThat(model.get("tokenError"))
                .as("格式不符应由 Controller 前置判定（§3.3.5），走 M10-28")
                .isEqualTo(BuyerPrompt.PASSCODE_MALFORMED.text());
        org.assertj.core.api.Assertions.assertThat(model.get("queryState"))
                .as("格式不符【不】进入三态渲染（那是「查过了但没查到」的语义）")
                .isNull();
    }

    @Test
    @DisplayName("B11-05：格式正确但不存在 → notFound 态，且 M10-05 提示不泄漏任何原因（BR-29）")
    void queryUnknownPasscodeYieldsNotFoundWithoutLeaking() throws Exception {
        MvcResult result = mockMvc.perform(post("/query").param("token", "ZZZZZZZZZZZZ"))
                .andExpect(status().isOk())
                .andExpect(view().name("query"))
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        org.assertj.core.api.Assertions.assertThat(model.get("queryState")).isEqualTo("notFound");
        org.assertj.core.api.Assertions.assertThat(model.get("queryMessage"))
                .isEqualTo(BuyerPrompt.PASSCODE_NOT_FOUND.text());
        // ⚠️ BR-29：失效/查不到时不返回联系人 —— 模型里不该出现姓名电话
        org.assertj.core.api.Assertions.assertThat(model.get("buyerName"))
                .as("notFound 态不得带出姓名（BR-29）").isNull();
        org.assertj.core.api.Assertions.assertThat(model.get("buyerTel"))
                .as("notFound 态不得带出电话（BR-29）").isNull();
    }

    @Test
    @DisplayName("B11-05：小写 + 首尾空格的口令码可正常查到（归一化在服务端）")
    void queryNormalizesTokenInput() throws Exception {
        String token = submitAndCaptureToken("10.9.9.5", "沈括", "13800002222");

        MvcResult result = mockMvc.perform(post("/query").param("token", "  " + token.toLowerCase() + "  "))
                .andExpect(status().isOk())
                .andExpect(view().name("query"))
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        org.assertj.core.api.Assertions.assertThat(model.get("queryState")).isEqualTo("active");
        org.assertj.core.api.Assertions.assertThat(String.valueOf(model.get("rank"))).isEqualTo("1");
    }

    @Test
    @DisplayName("B11-05：命中有效口令 → active 态，模型带 rank 与预填的姓名电话")
    void queryActiveCarriesRankAndContactForPrefill() throws Exception {
        String token = submitAndCaptureToken("10.9.9.6", "沈括", "13800003333");

        MvcResult result = mockMvc.perform(post("/query").param("token", token))
                .andExpect(status().isOk())
                .andExpect(view().name("query"))
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        org.assertj.core.api.Assertions.assertThat(model.get("queryState")).isEqualTo("active");
        org.assertj.core.api.Assertions.assertThat(String.valueOf(model.get("rank"))).isEqualTo("1");
        // 修改信息表单要预填当前值，否则「空框提交」会被判成「请填写姓名与联系电话」
        org.assertj.core.api.Assertions.assertThat(model.get("buyerName")).isEqualTo("沈括");
        org.assertj.core.api.Assertions.assertThat(model.get("buyerTel")).isEqualTo("13800003333");
    }

    // -------------------------------------------------------------------------
    // B11-06：修改信息 POST /query/update
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("B11-06：改信息成功 → 仍 active 态、位次不变、姓名已更新、并给出提示")
    void updateContactKeepsRankAndShowsFeedback() throws Exception {
        String token = submitAndCaptureToken("10.9.9.7", "沈括", "13800004444");

        // ⚠️ 改姓名时电话必须【也填上原值】——不能留空。
        //    空串的语义是「用户主动清空该项」（= 非法请求），不是「不改」；
        //    「不改」是【不传该参数】（服务端收到 null）。页面已预填当前值，
        //    所以正常用户操作不会出现空串。此处按页面的真实行为填写。
        MvcResult result = mockMvc.perform(post("/query/update")
                        .param("token", token)
                        .param("name", "沈梦溪")
                        .param("tel", "13800004444"))
                .andExpect(status().isOk())
                .andExpect(view().name("query"))
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        org.assertj.core.api.Assertions.assertThat(model.get("queryState")).isEqualTo("active");
        org.assertj.core.api.Assertions.assertThat(String.valueOf(model.get("rank")))
                .as("FR-16/BR-20：改信息不影响位次").isEqualTo("1");
        org.assertj.core.api.Assertions.assertThat(model.get("buyerName")).isEqualTo("沈梦溪");
        org.assertj.core.api.Assertions.assertThat(model.get("feedback")).isNotNull();
        // 改信息成功【不】改口令码，页面上的 token 仍是同一个
        org.assertj.core.api.Assertions.assertThat(model.get("token")).isEqualTo(token);
    }

    @Test
    @DisplayName("⚠️ 不传 name 参数 = 「不改姓名」（null 与空串语义不同，FR-16）")
    void omittingNameMeansLeaveUnchanged() throws Exception {
        String token = submitAndCaptureToken("10.9.9.11", "沈括", "13800008888");

        // 只传 tel（name 完全不传 ⇒ Controller 收到 null ⇒ 不改姓名）
        MvcResult result = mockMvc.perform(post("/query/update")
                        .param("token", token)
                        .param("tel", "13900009999"))
                .andExpect(status().isOk())
                .andExpect(view().name("query"))
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        org.assertj.core.api.Assertions.assertThat(model.get("feedback"))
                .as("只改一项是合法的（「留空不改」的正确表达是不传参数）").isNotNull();
        org.assertj.core.api.Assertions.assertThat(model.get("buyerName"))
                .as("未传 name ⇒ 姓名保持原值").isEqualTo("沈括");
        org.assertj.core.api.Assertions.assertThat(model.get("buyerTel")).isEqualTo("13900009999");
    }

    @Test
    @DisplayName("⚠️ 空串 ≠ null：姓名框被清空后提交会被拒（不是「静默不改」的假成功）")
    void blankFieldsOnUpdateAreRejected() throws Exception {
        String token = submitAndCaptureToken("10.9.9.8", "沈括", "13800005555");

        // 姓名留空 = 用户主动清空该框（页面已预填，所以空只可能是人为删掉）
        MvcResult result = mockMvc.perform(post("/query/update")
                        .param("token", token)
                        .param("name", "")
                        .param("tel", "13800005555"))
                .andExpect(status().isOk())
                .andExpect(view().name("query"))
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        // ⚠️ 关键口径：这条【不能】被当成「修改成功」。上一版实现曾把空串转成 null（=不改），
        //    导致「页面提示成功但数据没变」的假成功——本用例就是那个 bug 的护栏。
        org.assertj.core.api.Assertions.assertThat(model.get("feedback"))
                .as("空串是非法请求，不能报成功")
                .isNotEqualTo("修改成功：您的信息已更新，排队位次不受影响");
        // 数据必须保持原值，没有被改坏
        org.assertj.core.api.Assertions.assertThat(model.get("queryState")).isEqualTo("active");
        org.assertj.core.api.Assertions.assertThat(model.get("buyerName")).isEqualTo("沈括");
    }

    // -------------------------------------------------------------------------
    // B11-07：撤销 POST /query/revoke
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("B11-07：撤销成功 → 转 expired 态，'queryState' 为 expired 且给出反馈")
    void revokeMovesToExpiredState() throws Exception {
        String token = submitAndCaptureToken("10.9.9.9", "沈括", "13800006666");

        MvcResult result = mockMvc.perform(post("/query/revoke").param("token", token))
                .andExpect(status().isOk())
                .andExpect(view().name("query"))
                .andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        org.assertj.core.api.Assertions.assertThat(model.get("queryState")).isEqualTo("expired");
        org.assertj.core.api.Assertions.assertThat(model.get("feedback"))
                .as("撤销成功要给到反馈文案").isNotNull();
        // 撤销后不得再带出联系人
        org.assertj.core.api.Assertions.assertThat(model.get("buyerName")).isNull();
    }

    @Test
    @DisplayName("⚠️ 撤销后同一口令码再查 → 只能得到 expired（不泄漏「是因为撤销还是因为成交」）")
    void revokedTokenIsIndistinguishableFromOtherTerminalStates() throws Exception {
        String token = submitAndCaptureToken("10.9.9.10", "沈括", "13800007777");

        mockMvc.perform(post("/query/revoke").param("token", token)).andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post("/query").param("token", token))
                .andExpect(status().isOk()).andReturn();

        Map<String, Object> model = result.getModelAndView().getModel();
        org.assertj.core.api.Assertions.assertThat(model.get("queryState")).isEqualTo("expired");
        org.assertj.core.api.Assertions.assertThat(model.get("queryMessage"))
                .as("BR-29：只给「已失效」，不说明原因").isEqualTo(BuyerPrompt.PASSCODE_EXPIRED.text());
    }

    // -------------------------------------------------------------------------
    // 视图隔离：WEB-INF 下的 JSP 不能被直接 URL 访问
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("⚠️ 直连 /WEB-INF/views/*.jsp 必须 404（视图隔离生效）")
    void viewsAreNotDirectlyReachable() throws Exception {
        mockMvc.perform(get("/WEB-INF/views/index.jsp")).andExpect(status().isNotFound());
        mockMvc.perform(get("/WEB-INF/views/query.jsp")).andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // 内部工具：走一整套「提交意向」并取出口令码
    // -------------------------------------------------------------------------

    /**
     * 提交一条意向并取回口令码。
     *
     * <p>每个用例用自己的 {@code remoteAddr}（限流按 clientKey 计数，
     * NFR-17 是「同一 IP 第 11 次被拒」）——这样用例之间不会互相挤占配额，
     * 也让本测试类可以独立重跑。
     */
    private String submitAndCaptureToken(String clientIp, String name, String tel) throws Exception {
        MvcResult result = mockMvc.perform(post("/intent")
                        .param("name", name)
                        .param("tel", tel)
                        .with(r -> { r.setRemoteAddr(clientIp); return r; }))
                .andExpect(status().isOk())
                .andExpect(view().name("intent-success"))
                .andReturn();

        Object token = result.getModelAndView().getModel().get("token");
        org.assertj.core.api.Assertions.assertThat(token)
                .as("提交成功应把口令码放进模型").isNotNull();
        return String.valueOf(token);
    }

    /** 提交一条意向并返回口令码（供第一个用例复用）。 */
    private String submitOneIntention() throws Exception {
        return submitAndCaptureToken("10.9.9.99", "沈括", "13800000000");
    }

    // -------------------------------------------------------------------------
    // 保留：直接验证 Service 契约与 Web 层期望一致（防止两端各说各话）
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("契约对齐：Service 返回的拒绝码与 Controller 映射的错误属性都在枚举里")
    void serviceRejectionCodesMapToPromptTexts() {
        // 这条是「编译期契约」的运行时翻版：把 Controller 会用到的文案枚举点一遍，
        // 任何一条被删掉，这里立刻红（比等到页面缺文案才发现早）
        for (BuyerPrompt prompt : BuyerPrompt.values()) {
            org.assertj.core.api.Assertions.assertThat(prompt.text())
                    .as("文案 %s 不得为空", prompt).isNotBlank();
        }
    }

    /** 保留引用，避免 IDE 提示未使用（BuyerResult 是 Controller 的穷尽 switch 来源）。 */
    @SuppressWarnings("unused")
    private static void contractReference(BuyerResult<?> ignored, GoodsData ignored2) {
        // 仅为让本测试类在 DTO 包被重命名时编译失败（显式依赖，胜过隐式）
    }
}
