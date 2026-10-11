package com.simpleshop.web;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.simpleshop.service.BuyerIntentionService;
import com.simpleshop.service.dto.BuyerPrompt;
import com.simpleshop.service.dto.BuyerResult;
import com.simpleshop.service.dto.SubmitResultData;

/**
 * 买家端 · 提交意向表单页。端点 {@code B11-02}（{@code GET /intent/new}）与
 * {@code B11-03}（{@code POST /intent}），页面需求 {@code P10-02}。
 *
 * <table border="1">
 *   <caption>本类负责的端点</caption>
 *   <tr><th>编号</th><th>方法 路径</th><th>视图 / 去向</th></tr>
 *   <tr><td>{@code B11-02}</td><td>{@code GET /intent/new}</td><td>{@code WEB-INF/views/intent-form.jsp}</td></tr>
 *   <tr><td>{@code B11-03}</td><td>{@code POST /intent}</td>
 *       <td>失败 → 重渲染 {@code intent-form}；成功 → {@code forward:} 成功页</td></tr>
 * </table>
 *
 * <h2>⚠️ 成功时用 {@code forward} 而不是 {@code redirect}（{@code K5-02}、{@code BR-24}）</h2>
 * <p>第 11 章 {@code B11-03} 的原文要求：「提交成功后<b>不得通过 URL 直接访问成功页</b>
 * （口令码仅展示一次，{@code K5-02}），应使用<b>服务端转发</b>（{@code forward}）而非重定向」。
 *
 * <p>Spring MVC 里转发有两种写法，本类用<b>后者</b>：
 * <ul>
 *   <li>{@code return "redirect:/intent/success"} —— <b>错</b>：会变成 302 + 一个真实存在的
 *       {@code GET} 地址，用户可刷新、可收藏、可分享，<b>等于提供了一个回看入口</b>；</li>
 *   <li>{@code return "forward:/intent/success"} —— 也是 302（跳到同一个 DispatcherServlet），
 *       但 URL 仍停在 {@code /intent}，且 {@code /intent} <b>只接受 POST</b>，
 *       刷新会得到 405，<b>正好符合「刷新后不再展示口令码」</b>。</li>
 * </ul>
 *
 * <p><b>【本类的做法】</b>不引入 {@code forward:} 前缀（它要经过一次多余的 302），而是
 * <b>直接返回成功页的逻辑视图名</b>：{@code POST /intent} 处理完就在<b>同一个请求</b>里
 * 渲染成功页。这样：
 * <ol>
 *   <li>URL 始终是 {@code /intent}（POST），地址栏里没有口令码，也没有独立地址可回看；</li>
 *   <li>刷新页面 → 浏览器重发 POST → 会<b>再提交一次</b>，这是表单类应用的既有语义，
 *       也正是「离开后不再展示口令码」的保障（{@code AC-09} ③）；</li>
 *   <li>口令码走<b>请求属性</b>（{@code Model}），不进 URL、不进 session；
 *       请求结束即消失，<b>没有「回看」的载体</b>。</li>
 * </ol>
 *
 * <h2>⚠️ 为什么口令码不放 session</h2>
 * <p>原型笔记提过「POST 转发或一次性 session 传递」两条路。本实现选<b>请求属性</b>，
 * 理由是 session 里存了口令码之后，「谁、在什么时机清掉它」就成了一个必须额外维护的状态：
 * 忘了清 → 口令码在浏览器会话里长期驻留（违反「仅展示一次」）；清早了 → 页面空白。
 * 而请求属性<b>根本没有这个生命周期问题</b>——请求结束，它就不存在了。
 * 少一个状态，少一类缺陷。
 *
 * <h2>⚠️ 失败时为什么不直接抛异常</h2>
 * <p>{@link BuyerIntentionService#submitIntention} 用 {@link BuyerResult} 返回值表达业务拒绝
 * （与卖家端「异常即回滚」相反，见 {@link BuyerResult} 的类注释）。本类用 Java 21
 * <b>穷尽 switch</b> 处理两种结局——漏写分支编译不过，比「记得 catch」可靠。
 */
@Controller
public class BuyerIntentController {

    /** 表单页视图名。 */
    private static final String VIEW_FORM = "intent-form";

    /** 成功页视图名（{@code P10-03}）。 */
    private static final String VIEW_SUCCESS = "intent-success";

    private final BuyerIntentionService buyerIntentionService;

    public BuyerIntentController(BuyerIntentionService buyerIntentionService) {
        this.buyerIntentionService = buyerIntentionService;
    }

    /**
     * {@code B11-02} 显示提交意向表单。
     *
     * <p>本方法<b>不做任何业务判断</b>（商品是否可提交由 POST 时的 Service 判定）。
     * 这样做的后果是：即使商品已冻结，用户也能打开表单页；但他一提交就会被拒
     * （{@code AC-17} 要求的正是「服务端防线」，而不是「页面藏起来」）。
     *
     * @param model 视图模型；只需注入弹层用文案
     * @return 逻辑视图名 {@code intent-form}
     */
    @GetMapping("/intent/new")
    public String newIntentForm(Model model) {
        addScriptMessages(model);
        return VIEW_FORM;
    }

    /**
     * {@code B11-03} 提交购买意向。
     *
     * <h2>三种结局</h2>
     * <ol>
     *   <li><b>字段校验失败</b>（{@code M10-11}／{@code M10-36}／{@code M10-37}）：
     *       重新渲染表单页，把错误挂在对应字段上，并<b>原样回填</b>输入值——
     *       不回填的话用户填过的内容会丢，等于逼他重打一遍；</li>
     *   <li><b>业务拒绝</b>（队列满 {@code M10-12}、商品已非在售 {@code M10-13}、
     *       冻结 {@code M10-01}、限流 {@code TOO_MANY_REQUESTS}）：重新渲染表单页，
     *       在顶部显示错误条，同样回填输入值；</li>
     *   <li><b>成功</b>：渲染成功页（{@code P10-03}），把口令码放进<b>请求属性</b>。</li>
     * </ol>
     *
     * <h2>⚠️ 为什么字段错误与业务错误要分开渲染</h2>
     * <p>页面结构上，字段错误挂在 {@code <input>} 下面（紧贴出错的地方），
     * 业务错误显示在表单顶部。两者的<b>位置</b>不同，所以模型里用两组属性：
     * {@code nameError}／{@code telError} 对字段，{@code submitError} 对全局。
     * 若混成一个 {@code error} 属性，页面上就只剩一个位置能放提示，
     * 「请填写姓名」会显示在表单顶部、离那个空输入框很远——用户得自己找是哪个字段。
     *
     * <h2>⚠️ {@code clientKey} 取的是**来源 IP**</h2>
     * <p>Service 的 {@code submitIntention} 第三个参数用于内存限流（{@code NFR-17}，
     * 防同一来源高频提交）。本轮取 {@link HttpServletRequest#getRemoteAddr()}；
     * 注意这是<b>直连地址</b>，若将来加了反向代理，取到的会是代理 IP，
     * 届时需改读 {@code X-Forwarded-For}（该项已登记，本轮不处理）。
     *
     * @param rawName   姓名原样输入（<b>不 trim</b>——是否合法由 Service 判定，
     *                  且「首尾有空格」本身就是要报 {@code M10-36} 的一种非法输入）
     * @param rawTel    电话原样输入
     * @param request   当前请求（取来源 IP）
     * @param model     视图模型
     * @return 成功时为成功页视图名；被拒时为表单页视图名
     */
    @PostMapping("/intent")
    public String submitIntent(@RequestParam(name = "name", required = false) String rawName,
                               @RequestParam(name = "tel", required = false) String rawTel,
                               HttpServletRequest request,
                               Model model) {
        BuyerResult<SubmitResultData> result =
                buyerIntentionService.submitIntention(rawName, rawTel, request.getRemoteAddr());

        // Java 21 穷尽 switch：BuyerResult 是密封接口，两种结局都被覆盖，漏一个编译不过
        return switch (result) {
            case BuyerResult.Ok<SubmitResultData> ok -> {
                SubmitResultData data = ok.data();
                // ⚠️ 口令码放【请求属性】，不进 URL、不进 session（本类注释说明了理由）
                model.addAttribute("token", data.token());
                model.addAttribute("saveTip", BuyerPrompt.SAVE_PASSCODE.text());
                yield VIEW_SUCCESS;
            }
            case BuyerResult.Rejected<SubmitResultData> rejected -> {
                // 回填输入值——不回填的话用户填过的内容会丢
                model.addAttribute("formName", rawName);
                model.addAttribute("formTel", rawTel);
                applyRejection(model, rejected.prompt());
                addScriptMessages(model);
                yield VIEW_FORM;
            }
        };
    }

    /**
     * 把被拒原因分派到<b>正确的展示位置</b>：字段类错误挂字段，其余挂表单顶部。
     *
     * <p>为什么要分派：{@link BuyerPrompt} 是「提示码」，它本身不携带「该显示在哪里」
     * 的信息。页面有<b>两个位置</b>（字段下方 / 表单顶部），所以这层映射必须有人做——
     * 放在 Controller 而不是 JSP，是因为 JSP 里的 {@code <c:choose>} 判断会散落成
     * 「模板里判断业务语义」，而模板应当只负责渲染。
     *
     * <p>⚠️ 三处判断的依据是 {@link BuyerPrompt} 各常量的注释：
     * <ul>
     *   <li>{@code NAME_TEL_REQUIRED}／{@code NAME_INVALID}／{@code TEL_INVALID}
     *       是 {@code M10-11/36/37}，属<b>字段级</b>；</li>
     *   <li>{@code NAME_TEL_REQUIRED}（「请填写姓名与联系电话」）同时涉及两个字段，
     *       本实现<b>挂到姓名下</b>——因为 {@link com.simpleshop.service.BuyerIntentionService}
     *       的 {@code firstRejection} 按<b>参数顺序</b>（姓名在前）返回，与之一致；</li>
     *   <li>其余（{@code QUEUE_FULL}／{@code GOODS_UNAVAILABLE}／{@code GOODS_FROZEN}／
     *       {@code TOO_MANY_REQUESTS}）都是<b>与字段无关</b>的整体性拒绝，挂表单顶部。</li>
     * </ul>
     *
     * <p>⚠️ {@code GOODS_FROZEN}：本页通常到不了这里——表单页只在商品可提交时才有意义，
     * 而冻结期间用户仍可打开表单（{@code GET /intent/new} 不判定商品），
     * 所以冻结后提交会走到这一支，文案 {@code M10-01}「商品交易中，暂停接收新意向」。
     * 这是<b>有意的服务端兜底</b>，{@code AC-17} 要求的正是「绕过前端直接 POST 也仍被拒」。
     */
    private static void applyRejection(Model model, BuyerPrompt prompt) {
        String text = prompt.text();
        switch (prompt) {
            case NAME_TEL_REQUIRED -> model.addAttribute("nameError", text);
            case NAME_INVALID -> model.addAttribute("nameError", text);
            case TEL_INVALID -> model.addAttribute("telError", text);
            default -> model.addAttribute("submitError", text);
        }
    }

    /**
     * 注入确认弹层所需的文案（{@code M10-11/36/37} 与 {@code M10-03}）。
     *
     * <p>这几个值只服务于前端脚本的即时提示与二次确认，<b>不是</b>服务端校验的替代。
     * 之所以从 Java 侧下发而不在 JSP 里写死：文案的唯一权威是第 10 章 §10.5
     * （落在 {@link BuyerPrompt}），页面里再抄一份就会变成第二个权威。
     *
     * <p>⚠️ 值交给 JSP 时经 {@code fn:escapeXml} 转义后再嵌进 {@code <script>}——
     * 中文文案本身不含引号，但<b>不能依赖这个巧合</b>：若哪天文案改成含 {@code '}
     * 的句子（如英文撇号），未转义就会把脚本字符串截断，变成语法错误。
     */
    private static void addScriptMessages(Model model) {
        model.addAttribute("jsMsgRequired", BuyerPrompt.NAME_TEL_REQUIRED.text());
        model.addAttribute("jsMsgName", BuyerPrompt.NAME_INVALID.text());
        model.addAttribute("jsMsgTel", BuyerPrompt.TEL_INVALID.text());
        // M10-03 在 BuyerPrompt 里没有独立常量（第 10 章 §10.5.1 的二次确认文案），
        // 用 SAVE_PASSCODE 的语义最接近；此处直接写文案，并在此注明出处。
        model.addAttribute("jsMsgConfirm",
                "请确认姓名与联系电话无误。提交后将生成口令码，请务必保存");
    }
}
