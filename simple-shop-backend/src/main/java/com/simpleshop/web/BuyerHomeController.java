package com.simpleshop.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.simpleshop.service.BuyerIntentionService;
import com.simpleshop.service.dto.BuyerPrompt;
import com.simpleshop.service.dto.GoodsData;

/**
 * 买家端 · 当前商品页（首页）。端点 {@code B11-01}（{@code GET /}），页面需求 {@code P10-01}。
 *
 * <table border="1">
 *   <caption>本类负责的端点</caption>
 *   <tr><th>编号</th><th>方法 路径</th><th>视图</th></tr>
 *   <tr><td>{@code B11-01}</td><td>{@code GET /}</td><td>{@code WEB-INF/views/index.jsp}</td></tr>
 * </table>
 *
 * <h2>⚠️ 为什么是 {@code @Controller} 而不是 {@code @RestController}</h2>
 * <p>买家端是<b>服务端渲染</b>（{@code C-05}、{@code A-02}）：接口层<b>不出 REST 契约</b>，
 * 本类的方法是返回<b>逻辑视图名</b>由视图解析器渲染 JSP，
 * 而不是把对象序列化成 JSON。{@code @RestController} 会给每个方法加上
 * {@code @ResponseBody}，那样返回的字符串会被当成响应体直接写回浏览器——
 * 页面会变成一行 {@code index} 字面量。
 *
 * <h2>⚠️ 本端点不需要鉴权，且【不得】纳入卖家端拦截器</h2>
 * <p>买家端的身份凭证是<b>口令码</b>（{@code O-01}、{@code BR-32}），
 * 浏览商品是公开行为（{@code FR-12}）。
 * {@link WebMvcConfig} 只把 {@link SellerAuthInterceptor} 挂在
 * {@link WebMvcConfig#SELLER_API_PREFIX}（{@code /api/seller/**}）上，
 * 本类路径不在其覆盖范围内——这是<b>有意为之</b>，不是遗漏。
 * 若哪天有人把 {@code /**} 加进拦截器，买家端会整体 401，且首页直接打不开。
 *
 * <h2>⚠️ 空态与冻结态是<b>同一个页面的两种表现</b>，不是两个页面</h2>
 * <p>「无当前商品」与「商品已冻结」都由本方法返回同一个视图 {@code index}，
 * 差别<b>只在数据</b>：无商品时 {@code browseCurrentGoods()} 返回 {@code null}，
 * 冻结时返回 {@code status = frozen} 的 {@link GoodsData}。
 * 页面据此选择渲染哪个分支（见 {@code index.jsp} 的 {@code <c:if>}）。
 * 这与 {@code I11-04}「无商品时 {@code data: null} + {@code code: 0}」是同一条口径——
 * 「没有商品」不是错误，不该走错误码。
 *
 * <h2>⚠️ 文案不在本类写死，一律取 {@link BuyerPrompt}</h2>
 * <p>第 10 章 §10.5《提示文案表》是文案的<b>唯一权威</b>。本类只把枚举的
 * {@link BuyerPrompt#text()} 放进模型，页面不出现中文字面量——
 * 这样「改文案」只有一个地方要改，且不会出现「页面写的是旧版文案、验收对照的是新版」这类偏差。
 * 注意 {@code M10-01} 对<b>手动冻结与交易冻结两种来源是同一句</b>（{@code AC-17} 明文要求），
 * 故这里<b>不</b>按 {@code freeze_by} 分文案。
 *
 * <h2>⚠️ 为什么 DTO 要先摊平成 {@code Map} 再交给 JSP（不是绕路，是必须）</h2>
 * <p>本项目的响应 DTO（{@link GoodsData}、
 * {@link com.simpleshop.service.dto.PasscodeQueryResult} …）<b>全部是 Java {@code record}</b>。
 * 而 JSP 的 EL 表达式<b>只认 JavaBeans 规范</b>：写 {@code ${goods.status}} 时它去找
 * {@code getStatus()}，而 record 的访问器叫 {@code status()}——<b>两者对不上</b>。
 *
 * <p><b>【实测证据，非推断】</b>在 Tomcat 10.1.55（Spring Boot 3.5.x 自带）下直接写
 * {@code ${goods.status}} 会抛下面这个异常，页面直接 500：
 * <pre>
 * jakarta.el.PropertyNotFoundException:
 *   The class 'com.simpleshop.service.dto.GoodsData' does not have the property 'status'.
 * </pre>
 *
 * <p><b>【为什么不用 {@code ${goods.status()}} 这种「方法调用」写法】</b>
 * EL 3.0 确实支持方法调用语法，但它在各 EL 实现中的可用性<b>并不一致</b>——
 * OmniFaces 作者 BalusC 的原文是
 * "It worked for me in WildFly 21, but it has been reported to not work in other EL implementations"。
 * 把全部页面押在一个「某些实现才支持」的特性上，等于埋一个「换个容器就全线 500」的雷，
 * 而本项目恰恰还有 {@code OPEN-01} 容器化、以及「打 war 部署到外部独立 Tomcat」两条待定路线。
 *
 * <p><b>【为什么也不自定义 ELResolver】</b>那是<b>全局改写 EL 求值行为</b>的改动，
 * 影响所有 JSP、且需在 ServletContext 初始化期注册；为几个字段引入这种机制不成比例。
 *
 * <p><b>【本类的做法】</b>用 Java 21 的<b>穷尽 switch</b> 处理完 Service 结果之后，
 * 把需要展示的字段<b>逐字段显式</b>搬进 {@code Map} 再交给视图。三个好处：
 * <ol>
 *   <li>JSP 里照旧写 {@code ${goods.name}}——EL 对 {@code Map} 的属性访问就是 key 查找，
 *       这是 EL 规范行为，<b>任何实现都支持</b>，没有上文那种版本风险；</li>
 *   <li>与项目既有取向一致：{@code BuyerResult} 的类注释给的示例就是「调用方用穷尽 switch 处理」，
 *       <b>判断留在 Java 里，模板只管渲染</b>；</li>
 *   <li>逐字段显式列举，符合设计说明书 §4.6.4「映射必须显式列字段、不用反射式拷贝」。</li>
 * </ol>
 */
@Controller
public class BuyerHomeController {

    private final BuyerIntentionService buyerIntentionService;

    public BuyerHomeController(BuyerIntentionService buyerIntentionService) {
        this.buyerIntentionService = buyerIntentionService;
    }

    /**
     * {@code B11-01} 当前商品页。
     *
     * <p>返回 {@code goods} 字段的可能取值：
     * <ul>
     *   <li>{@code null} —— 无当前商品（表内无行，或商品为 {@code off_sale}）⇒ 页面走空态；</li>
     *   <li>{@code status = on_sale} —— 正常展示，提交入口可用；</li>
     *   <li>{@code status = frozen} —— 展示卡片与「已冻结」标签，提交入口<b>置灰保留</b>（{@code 10-B}）。
     *       冻结期间商品详情<b>仍可浏览</b>（{@code BR-14}）。</li>
     * </ul>
     *
     * <p>⚠️ <b>本方法不做任何业务判断</b>：有没有当前商品、是什么状态，
     * 全由 Service 判定。页面只负责按拿到的数据显示。
     *
     * @param model 视图模型；写入 {@code goods} 与两条提示文案
     * @return 逻辑视图名 {@code index} → {@code /WEB-INF/views/index.jsp}
     */
    @GetMapping("/")
    public String currentGoods(Model model) {
        GoodsData goods = buyerIntentionService.browseCurrentGoods();

        model.addAttribute("goods", toViewMap(goods));
        model.addAttribute("promptGoodsFrozen", BuyerPrompt.GOODS_FROZEN.text());
        model.addAttribute("promptNoGoods", BuyerPrompt.NO_CURRENT_GOODS.text());

        return "index";
    }

    /**
     * 把 {@link GoodsData} 摊平成 JSP 的 EL 可直接读取的 {@code Map}。
     *
     * <p>为什么要摊平、为什么不用 {@code ${goods.status()}}——见类注释的那一节，
     * 那里有实测的异常栈与三条方案的取舍理由，此处不再复述。
     *
     * <p>⚠️ <b>逐字段显式列举，不用反射遍历 record components</b>：
     * 反射式拷贝会静默把 DTO 以后新增的字段带进页面，
     * 而 §11.1「接口层不得引入未登记字段」要求字段是<b>被明确挑选</b>出来的
     * （同 §4.6.4）。这里多写几行，换的是「页面能看到哪些字段」有人为它签字。
     *
     * <p>⚠️ 用 {@link LinkedHashMap} 而非 {@code Map.of}：后者不允许 {@code null} 值，
     * 而 {@code description}、{@code picUrl}、{@code freezeBy}、{@code tradeStart} 都<b>可以</b>为
     * {@code null}（见 {@link GoodsData} 的字段表）。用 {@code Map.of} 会在首次遇到无描述的商品时
     * 抛 {@code NullPointerException}——那是个只在特定数据下才出现、测试很可能漏掉的故障。
     *
     * @param goods 当前商品；{@code null} 表示「无当前商品」，原样返回 {@code null}
     *              （页面用 {@code ${empty goods}} 判定，{@code null} 与空 Map 都为真）
     * @return 视图用的字段表；入参为 {@code null} 时为 {@code null}
     */
    private static Map<String, Object> toViewMap(GoodsData goods) {
        if (goods == null) {
            return null;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", goods.id());
        view.put("name", goods.name());
        view.put("description", goods.description());
        view.put("picUrl", goods.picUrl());
        view.put("price", goods.price());
        view.put("status", goods.status());
        view.put("freezeBy", goods.freezeBy());
        view.put("createAt", goods.createAt());
        view.put("tradeStart", goods.tradeStart());
        return view;
    }
}
