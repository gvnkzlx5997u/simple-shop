package com.simpleshop.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.simpleshop.service.BuyerIntentionService;
import com.simpleshop.service.PasscodeService;
import com.simpleshop.service.dto.BuyerPrompt;
import com.simpleshop.service.dto.BuyerResult;
import com.simpleshop.service.dto.PasscodeQueryResult;

/**
 * 买家端 · 口令码查询与操作页。端点 {@code B11-04} ~ {@code B11-07}，页面需求 {@code P10-04}。
 *
 * <table border="1">
 *   <caption>本类负责的端点</caption>
 *   <tr><th>编号</th><th>方法 路径</th><th>含义</th></tr>
 *   <tr><td>{@code B11-04}</td><td>{@code GET /query}</td><td>显示查询表单（初始态，无结果）</td></tr>
 *   <tr><td>{@code B11-05}</td><td>{@code POST /query}</td><td>查询（三态）</td></tr>
 *   <tr><td>{@code B11-06}</td><td>{@code POST /query/update}</td><td>修改姓名／电话</td></tr>
 *   <tr><td>{@code B11-07}</td><td>{@code POST /query/revoke}</td><td>撤销意向</td></tr>
 * </table>
 *
 * <h2>⚠️ 三个写操作都用 Post/Redirect/Get 的「变体」——但本页不能纯 PRG</h2>
 * <p>常见做法是：POST 处理完 → {@code redirect} 到 GET 页 → 刷新不会重复提交。
 * <b>本页不能这么做</b>，原因是查询结果依赖口令码，而口令码<b>不能进 URL</b>
 * （{@code BR-24}：不允许任何回看入口）。若 {@code redirect} 到
 * {@code GET /query?token=XXXX}，就等于把口令码写进了地址栏与浏览器历史——
 * 那正是 {@code BR-24} 要防的事。
 *
 * <p>因此本页的 POST 一律<b>直接渲染结果</b>（返回逻辑视图名）。代价是：刷新会重发 POST。
 * 对查询而言重发无害（只读）；对修改与撤销而言，重发同样是<b>幂等</b>的：
 * <ul>
 *   <li>改信息重发 → 还是改成同样的值，{@code queueOrder} 不变，结果相同；</li>
 *   <li>撤销重发 → 第二次进入时意向已是终态，返回 {@code PASSCODE_EXPIRED}
 *       （页面显示「该口令码已失效」）——<b>不会重复撤销、不会报错</b>。</li>
 * </ul>
 *
 * <h2>⚠️ 格式校验必须由本类先做（{@code PasscodeService.isWellFormed}）</h2>
 * <p>{@code BuyerIntentionService#queryByToken} 的注释明确写着：格式不符<b>不进入</b>查询方法，
 * 「由调用方先调 {@code isWellFormed} 判定」。
 * 原因是文案不同：格式不符是 {@code M10-28}「请输入 12 位口令码」，
 * 而「格式对但库里没有」是 {@code M10-05}「口令不正确」。
 * 若不先判格式，「输入 {@code abc}」会显示「口令不正确」——
 * 用户会以为「这个码是错的」，而实际上他连码都没输全。
 *
 * <h2>⚠️ 失效态绝不携带任何买家信息（{@code BR-29}）</h2>
 * <p>{@link PasscodeQueryResult.Expired} 是<b>零字段</b>记录，本类拿不到买家的姓名电话——
 * 「不泄漏」这件事由类型保证，不靠本类自觉。
 */
@Controller
public class BuyerQueryController {

    /** 查询页视图名。 */
    private static final String VIEW_QUERY = "query";

    private final BuyerIntentionService buyerIntentionService;

    public BuyerQueryController(BuyerIntentionService buyerIntentionService) {
        this.buyerIntentionService = buyerIntentionService;
    }

    /**
     * {@code B11-04} 显示查询表单（初始态）。
     *
     * @param model 视图模型
     * @return 逻辑视图名 {@code query}
     */
    @GetMapping("/query")
    public String queryForm(Model model) {
        // 初始态：不设置 queryState，页面按 <c:otherwise> 只渲染输入区
        addScriptMessages(model);
        return VIEW_QUERY;
    }

    /**
     * {@code B11-05} 凭口令码查询（三态）。
     *
     * <h2>处理顺序（顺序不可换）</h2>
     * <ol>
     *   <li><b>格式前置校验</b>：{@code isWellFormed} 不通过 → 渲染 {@code M10-28}，
     *       <b>不发起查询</b>；</li>
     *   <li>查询 → 四态之一：{@code NotFound}／{@code Expired}／{@code Active(queued)}／
     *       {@code Active(trading)}。</li>
     * </ol>
     *
     * <h2>⚠️ 位次由服务端算好下发，页面不做计算</h2>
     * <p>{@code Active.rank()} 是 Service 用 {@code countQueuedAhead + 1} 算出来的
     * （{@code §9.5.3}：位次是<b>计数派生</b>，不是存储字段）。
     * 页面只做「把 {@code N} 替换成这个数字」这一步字符串替换——
     * 若在页面里算位次，就得把整个队列数据传到前端，那是另一个量级的改动。
     *
     * @param rawToken 口令码原样输入
     * @param model    视图模型
     * @return 逻辑视图名 {@code query}
     */
    @PostMapping("/query")
    public String query(@RequestParam(name = "token", required = false) String rawToken,
                        Model model) {
        addScriptMessages(model);
        model.addAttribute("formToken", rawToken);

        // ① 格式前置校验（M10-28）：格式不符不发起查询
        if (!PasscodeService.isWellFormed(rawToken)) {
            model.addAttribute("tokenError", BuyerPrompt.PASSCODE_MALFORMED.text());
            return VIEW_QUERY;
        }

        // 归一化后作为「这条意向的钥匙」，后续 update/revoke 的表单里也要带它
        String normalized = PasscodeService.normalize(rawToken);
        model.addAttribute("token", normalized);

        // ② 查询 → 四态
        PasscodeQueryResult result = buyerIntentionService.queryByToken(rawToken);
        switch (result) {
            case PasscodeQueryResult.NotFound ignored -> {
                // M10-05：仅提示，不展示任何其他信息
                model.addAttribute("queryState", "notFound");
                model.addAttribute("queryMessage", BuyerPrompt.PASSCODE_NOT_FOUND.text());
            }
            case PasscodeQueryResult.Expired ignored -> {
                // M10-06：⚠️ 不得说明原因、不得返回买家信息（BR-29）
                // 该分支拿不到任何买家字段——Expired 是零字段 record
                model.addAttribute("queryState", "expired");
                model.addAttribute("queryMessage", BuyerPrompt.PASSCODE_EXPIRED.text());
            }
            case PasscodeQueryResult.Active active -> {
                model.addAttribute("queryState", "active");
                model.addAttribute("trading", active.trading());
                if (active.trading()) {
                    // M10-08：trading 不占位次（Active 的紧凑构造器保证 rank 必为 null）
                    model.addAttribute("queryMessage", BuyerPrompt.ENTERED_TRADE.text());
                    model.addAttribute("revokeTip", BuyerPrompt.REVOKE_NOT_ALLOWED.text());
                } else {
                    // M10-07：文案含占位符 N，页面替换
                    model.addAttribute("queryMessage", BuyerPrompt.QUEUED_RANK.text());
                    model.addAttribute("rank", active.rank());
                }
                model.addAttribute("editHint",
                        "修改姓名或电话不会影响您的排队位次");
                // ⚠️ 买家姓名电话由 Service 的查询结果带出。
                //    注意 PasscodeQueryResult.Active 只带 rank/trading，【不带】姓名电话——
                //    这是「最小暴露」的设计取向。本页要预填当前值，故需另取一次。
                fillContact(model, rawToken);
            }
        }
        return VIEW_QUERY;
    }

    /**
     * {@code B11-06} 修改姓名／电话。
     *
     * <h2>⚠️ 「留空 = 不改」与「留空 = 清空」的边界</h2>
     * <p>本页表单<b>预填了当前值</b>，所以用户看到的是「姓名框里已经有原来的姓名」。
     * 他若把姓名框清空再提交，提交的就是空串——按
     * {@code BuyerIntentionService#updateIntentionContact} 的注释，
     * 空串表示「用户把该项清空了」→ 字段校验失败 → {@code M10-11}。
     *
     * <p>这与「留空表示不修改」看起来矛盾，其实不是：
     * 页面上写的「留空表示不修改」指的是<b>保持原值不动</b>（即不改那个框）；
     * 一旦用户<b>主动清空</b>，服务端看到的就是「他填了空」。
     * 这个「空串 vs null」的语义差异是 Service 明确定义的口径，
     * 本类原样传参（把表单值直接交给 Service），不做自己的判断——
     * 否则就会出现「页面判一次、Service 再判一次」的两套规则。
     *
     * <h2>⚠️ 本方法成功后的处理：重新查询并渲染</h2>
     * <p>改完信息要「就地提示 + 结果区刷新」（{@code §10.3.4}）。
     * 刷新结果区最可靠的做法不是「用表单里的值更新页面」，
     * 而是<b>重新查一次</b>——这样页面上显示的一定是库里的真实值，
     * 而不是「页面以为提交成功了的那个值」。
     *
     * @param rawToken 口令码
     * @param newName  新姓名（<b>为 {@code null} 表示表单没带该字段</b>）
     * @param newTel   新电话
     * @param model    视图模型
     * @return 逻辑视图名 {@code query}
     */
    @PostMapping("/query/update")
    public String updateContact(@RequestParam(name = "token", required = false) String rawToken,
                                @RequestParam(name = "name", required = false) String newName,
                                @RequestParam(name = "tel", required = false) String newTel,
                                Model model) {
        addScriptMessages(model);
        String normalized = PasscodeService.normalize(rawToken);
        model.addAttribute("token", normalized);
        model.addAttribute("formToken", normalized);

        // ⚠️ 空串【原样】交给 Service，本类不把空串转成 null。
        //
        //    理由：页面已【预填当前值】，所以「姓名框是空的」只可能是用户【主动清空】的
        //    （留空不改 ≠ 清空，前者是「不动那个框」，框里仍是原值）。
        //    用户清空后再提交，意图是「我要把姓名改成空」——那是个【非法请求】，
        //    应当报 M10-11（请填写姓名与联系电话），而不是被静默当成「不改」。
        //
        //    ⚠️ 上一版实现把空串转成了 null（=不改），后果是：用户清空姓名框点提交，
        //    页面显示「修改成功」但什么都没变——一个【假的成功】。
        //    这正是 Service 注释里明确区分「null = 不改」与「空串 = 用户清空了」的原因。
        //    判定权在 IntentionValidator（Service 侧），本类不做自己的规则。
        BuyerResult<Void> result =
                buyerIntentionService.updateIntentionContact(normalized, newName, newTel);

        return switch (result) {
            case BuyerResult.Ok<Void> ignored -> {
                model.addAttribute("feedback", "修改成功：您的信息已更新，排队位次不受影响");
                renderAfterMutation(model, normalized);
                yield VIEW_QUERY;
            }
            case BuyerResult.Rejected<Void> rejected -> {
                // 把拒绝原因显示出来，并仍然渲染当前结果（让用户看到现状再改）
                applyUpdateRejection(model, rejected.prompt());
                renderAfterMutation(model, normalized);
                yield VIEW_QUERY;
            }
        };
    }

    /**
     * {@code B11-07} 撤销意向。
     *
     * <h2>⚠️ 撤销成功后口令码【立即失效】，页面随之转为失效态</h2>
     * <p>{@code BR-26}：口令码失效只看意向是否终态。撤销把意向置为 {@code revoked}（终态），
     * 因此同一口令码<b>立刻</b>不能再查。本方法成功后就按失效态渲染
     * （而不是继续展示买家信息）——否则用户会看到一个「已撤销但信息仍在」的页面，
     * 与「已退出队列」的语义不符。
     *
     * <p>⚠️ 提示语「撤销成功：您的意向已退出队列且不可恢复」是<b>操作回执</b>
     * （「我点了撤销」这件事的结果），不是「失效原因」。与 {@code BR-29}
     * 「失效提示不得说明原因」不冲突——{@code BR-29} 管的是<b>查询时</b>对失效码的说明，
     * 那是给「不知道发生了什么的人」看的；这里是给<b>刚执行了撤销的人</b>的回执。
     * 两者受众不同，故一个说明、一个不说明。
     *
     * @param rawToken 口令码
     * @param model    视图模型
     * @return 逻辑视图名 {@code query}
     */
    @PostMapping("/query/revoke")
    public String revoke(@RequestParam(name = "token", required = false) String rawToken,
                         Model model) {
        addScriptMessages(model);
        String normalized = PasscodeService.normalize(rawToken);
        model.addAttribute("formToken", normalized);

        BuyerResult<Void> result = buyerIntentionService.revokeIntention(normalized);

        return switch (result) {
            case BuyerResult.Ok<Void> ignored -> {
                // 撤销成功 ⇒ 口令码即刻失效（BR-26），按失效态渲染
                model.addAttribute("queryState", "expired");
                model.addAttribute("queryMessage", BuyerPrompt.PASSCODE_EXPIRED.text());
                model.addAttribute("feedback",
                        "撤销成功：您的意向已退出队列且不可恢复，该口令码同时失效");
                // ⚠️ 不放 token：操作区不再展开，也不需要它了
                yield VIEW_QUERY;
            }
            case BuyerResult.Rejected<Void> rejected -> {
                // 最常见的是 trading 时撤销 → REVOKE_NOT_ALLOWED（M10-14）
                model.addAttribute("feedback", rejected.prompt().text());
                renderAfterMutation(model, normalized);
                yield VIEW_QUERY;
            }
        };
    }

    // -------------------------------------------------------------------------
    // 内部工具
    // -------------------------------------------------------------------------

    /**
     * 修改／撤销之后重新渲染查询结果。
     *
     * <p>抽出来是因为三个写方法（update 成功／update 被拒／revoke 被拒）都要做同一件事：
     * 「重新查一次 → 把结果放进模型」。重新查而不是复用表单值，
     * 保证页面显示的是<b>库里的真实值</b>。
     *
     * @param model 视图模型
     * @param token 归一化后的口令码
     */
    private void renderAfterMutation(Model model, String token) {
        if (token == null || token.isEmpty()) {
            model.addAttribute("queryState", "notFound");
            model.addAttribute("queryMessage", BuyerPrompt.PASSCODE_NOT_FOUND.text());
            return;
        }
        PasscodeQueryResult result = buyerIntentionService.queryByToken(token);
        switch (result) {
            case PasscodeQueryResult.NotFound ignored -> {
                model.addAttribute("queryState", "notFound");
                model.addAttribute("queryMessage", BuyerPrompt.PASSCODE_NOT_FOUND.text());
            }
            case PasscodeQueryResult.Expired ignored -> {
                model.addAttribute("queryState", "expired");
                model.addAttribute("queryMessage", BuyerPrompt.PASSCODE_EXPIRED.text());
            }
            case PasscodeQueryResult.Active active -> {
                model.addAttribute("queryState", "active");
                model.addAttribute("trading", active.trading());
                if (active.trading()) {
                    model.addAttribute("queryMessage", BuyerPrompt.ENTERED_TRADE.text());
                    model.addAttribute("revokeTip", BuyerPrompt.REVOKE_NOT_ALLOWED.text());
                } else {
                    model.addAttribute("queryMessage", BuyerPrompt.QUEUED_RANK.text());
                    model.addAttribute("rank", active.rank());
                }
                model.addAttribute("editHint", "修改姓名或电话不会影响您的排队位次");
                fillContact(model, token);
            }
        }
    }

    /**
     * 取买家姓名／电话用于<b>结果区回显</b>与<b>改信息表单预填</b>。
     *
     * <h2>⚠️ 为什么要单独取一次</h2>
     * <p>{@link PasscodeQueryResult.Active} <b>只带</b> {@code rank} 与 {@code trading}，
     * <b>不带</b>买家的姓名电话——这是「最小暴露」的设计取向（该 DTO 的注释说明了理由）。
     * 但页面需要预填当前值（否则用户只改电话会被判「请填写姓名」），
     * 且改完信息要回显，所以必须另取。
     *
     * <p>⚠️ 本方法<b>只在 {@code Active} 分支被调用</b>——
     * 失效态绝不取买家信息（{@code BR-29}）。这一点由调用位置保证；
     * 且 {@link BuyerIntentionService#contactByToken} 内部也会<b>再挡一次</b>
     * （终态意向返回空），两道防线，任一处漏了另一处仍成立。
     *
     * <p>⚠️ 值为 {@code null} 时统一换成空串：库里 {@code name}／{@code tel}
     * 允许为空，而 {@code <input value="">} 与 {@code <c:out value="${null}"/>}
     * 都能处理空串，但 {@code null} 会让页面显示字面量或空白行，不如下游统一。
     *
     * @param model    视图模型
     * @param rawToken 口令码（原样或归一化均可）
     */
    private void fillContact(Model model, String rawToken) {
        buyerIntentionService.contactByToken(rawToken).ifPresent(c -> {
            model.addAttribute("buyerName", c[0] == null ? "" : c[0]);
            model.addAttribute("buyerTel", c[1] == null ? "" : c[1]);
        });
    }

    /**
     * 把「改信息」的被拒原因放到正确位置。
     *
     * <p>改信息页只有两个字段，所以映射比提交页简单；
     * 但「口令码类」的错误（{@code PASSCODE_NOT_FOUND}／{@code PASSCODE_EXPIRED}）
     * 不是字段问题，要放到页面顶部——它们意味着整条意向都不可操作了。
     */
    private static void applyUpdateRejection(Model model, BuyerPrompt prompt) {
        String text = prompt.text();
        switch (prompt) {
            case NAME_TEL_REQUIRED, NAME_INVALID -> model.addAttribute("updateNameError", text);
            case TEL_INVALID -> model.addAttribute("updateTelError", text);
            default -> model.addAttribute("feedback", text);
        }
    }

    /** 注入前端脚本所需的文案（M10-28、M10-09）。 */
    private static void addScriptMessages(Model model) {
        model.addAttribute("jsMsgMalformed", BuyerPrompt.PASSCODE_MALFORMED.text());
        model.addAttribute("jsMsgRevokeConfirm", "撤销后您的意向将退出队列且不可恢复，确定撤销？");
    }
}
