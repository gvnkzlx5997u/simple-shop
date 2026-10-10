package com.simpleshop.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.simpleshop.service.SellerIntentionService;
import com.simpleshop.service.dto.IntentionPageData;
import com.simpleshop.service.dto.PasscodeData;
import com.simpleshop.web.dto.IntentionIdRequest;
import com.simpleshop.web.dto.TradeFailureRequest;

/**
 * 意向与交易接口（{@code I11-10} ~ {@code I11-14}）。方案 §4.4。
 *
 * <table border="1">
 *   <caption>接口一览</caption>
 *   <tr><th>编号</th><th>方法 路径</th><th>出参 {@code data}</th></tr>
 *   <tr><td>{@code I11-10}</td><td>{@code GET /api/seller/product/intentions}</td>
 *       <td>分页 + {@code queue_count} + {@code items[]}，<b>或 {@code null}</b>（无商品）</td></tr>
 *   <tr><td>{@code I11-11}</td><td>{@code POST /api/seller/intention/trade}</td><td>{@code null}</td></tr>
 *   <tr><td>{@code I11-12}</td><td>{@code POST /api/seller/intention/success}</td><td>{@code null}</td></tr>
 *   <tr><td>{@code I11-13}</td><td>{@code POST /api/seller/intention/failure}</td><td>{@code null}</td></tr>
 *   <tr><td>{@code I11-14}</td><td>{@code GET /api/seller/intention/passcode}</td><td>{@code {token}}</td></tr>
 * </table>
 *
 * <h2>⚠️ 五条路径没有公共前缀，{@code I11-10} 尤其容易写错</h2>
 * <p>{@code I11-10} 挂在 <b>{@code /api/seller/product/intentions}</b>（复数 {@code product} 下），
 * 而 {@code I11-11}~{@code I11-14} 挂在 <b>{@code /api/seller/intention/*}</b>
 * （<b>单数</b> {@code intention}，没有 {@code product} 段）。
 * 因此本类<b>不</b>用类级 {@code @RequestMapping} 拼前缀——那必然要发明一个
 * 「既含 product 又含 intention」的假前缀，反而更容易把两条路径写混。
 * 路径以 {@code public static final} 常量给出，测试与文档直接引用，避免字符串字面量散落。
 *
 * <h2>⚠️ 入参位置也不统一</h2>
 * <ul>
 *   <li>{@code I11-10}：分页走 <b>query string</b>，无路径参数（单一商品槽位，{@code BR-11}）；</li>
 *   <li>{@code I11-11}~{@code I11-13}：{@code intention_id} 在 <b>请求体</b>里（{@code snake_case}）；</li>
 *   <li>{@code I11-14}：<b>{@code GET} + query 参数 {@code intention_id}</b>——它虽是只读查询，
 *       但因返回口令码<b>原文</b>，须记审计日志（{@code NFR-12}）。</li>
 * </ul>
 *
 * <h2>本类不含任何业务判断</h2>
 * <p>只做三件事：绑定请求 → 调 Service → 包 {@link ApiResponse}。
 * 唯一的「判断」是把请求 DTO 拆成方法参数，那是类型转换，不是规则。
 * <p>⚠️ <b>但分页参数上的 {@code @Min}／{@code @Max} 是例外且是刻意的</b>：
 * 它们不是业务规则，而是「报文里的数字是否可接受」——{@code page}／{@code page_size}
 * 在 §4.3 里<b>没有</b>专属业务码，故按 §4.6.3 的判据可以（也应当）留在注解上；
 * 并且 Service 仍<b>独立</b>校验一次（{@code G6-02}），两处都返回同一个 {@code 50002}。
 */
@RestController
public class SellerIntentionController {

    /** {@code I11-10} 查意向名单。路径在 {@code product} 下，见类注释。 */
    public static final String INTENTIONS_PATH = "/api/seller/product/intentions";

    /** {@code I11-11} 进入交易。 */
    public static final String TRADE_PATH = "/api/seller/intention/trade";

    /** {@code I11-12} 标记交易成功。 */
    public static final String SUCCESS_PATH = "/api/seller/intention/success";

    /** {@code I11-13} 标记交易失败。 */
    public static final String FAILURE_PATH = "/api/seller/intention/failure";

    /** {@code I11-14} 查看意向口令码。 */
    public static final String PASSCODE_PATH = "/api/seller/intention/passcode";

    private final SellerIntentionService sellerIntentionService;

    public SellerIntentionController(SellerIntentionService sellerIntentionService) {
        this.sellerIntentionService = sellerIntentionService;
    }

    /**
     * {@code I11-10} 查当前商品的意向名单（分页）。
     *
     * <p>无当前商品时 {@code data} 为 {@code null}、{@code code} 为 {@code 0}
     * （已定 Q-2、§4.5-A），与 {@code I11-04} 的空态同一口径。
     *
     * <p>每页默认 10 条（{@code 10-E}）；{@code items[]} 按 {@code queue_order} 升序，
     * <b>不含</b> {@code token}（{@code 11-D}），{@code trading} 行的 {@code rank} 为 {@code null}。
     *
     * @param page     页码（<b>从 1 起</b>，默认 1）
     * @param pageSize 每页条数（默认 10，上限 100）
     * @return 统一响应；{@code data} 为分页名单或 {@code null}（<b>键始终存在</b>）
     */
    @GetMapping(INTENTIONS_PATH)
    public ApiResponse<IntentionPageData> listIntentions(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "page_size", defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.ok(sellerIntentionService.listIntentions(page, pageSize));
    }

    /**
     * {@code I11-11} 对<b>队首</b>意向发起交易。
     *
     * <p>{@code 30002}（非队首）是<b>服务端防线</b>（{@code BR-12}、{@code AC-22}），
     * 不得只靠前端禁用第一行之外的按钮。
     *
     * @param request 含 {@code intention_id}
     * @return 统一响应；{@code data} 为 {@code null}（<b>键始终存在</b>）
     */
    @PostMapping(TRADE_PATH)
    public ApiResponse<Void> enterTrade(@Valid @RequestBody IntentionIdRequest request) {
        sellerIntentionService.enterTrade(request.getIntentionId());
        return ApiResponse.ok();
    }

    /**
     * {@code I11-12} 标记交易成功（整体一个事务，顺序不可调换，{@code BR-03}）。
     *
     * @param request 含处于 {@code trading} 的 {@code intention_id}
     * @return 统一响应；{@code data} 为 {@code null}
     */
    @PostMapping(SUCCESS_PATH)
    public ApiResponse<Void> markTradeSuccess(@Valid @RequestBody IntentionIdRequest request) {
        sellerIntentionService.markTradeSuccess(request.getIntentionId());
        return ApiResponse.ok();
    }

    /**
     * {@code I11-13} 标记交易失败，并裁决为「作废」或「重新排队」。
     *
     * <p>{@code disposal} 与 {@code fail_reason} 的取值校验都在 Service
     * （{@code 30006}／{@code 30007}，均为 HTTP 200）——见 {@link TradeFailureRequest} 的说明。
     *
     * @param request 含 {@code intention_id}、{@code disposal}、可空的 {@code fail_reason}
     * @return 统一响应；{@code data} 为 {@code null}
     */
    @PostMapping(FAILURE_PATH)
    public ApiResponse<Void> markTradeFailure(@Valid @RequestBody TradeFailureRequest request) {
        sellerIntentionService.markTradeFailure(
                request.getIntentionId(), request.getDisposal(), request.getFailReason());
        return ApiResponse.ok();
    }

    /**
     * {@code I11-14} 查看某意向的口令码<b>原文</b>。
     *
     * <p>终态意向 → {@code 40002}（{@code NFR-16}：不展示）；意向不存在 → {@code 30008}。
     * 成功时记审计日志（{@code NFR-12} 第 7 类），<b>但日志里不含码值</b>（{@code 12-H}）。
     *
     * @param intentionId query 参数 {@code intention_id}
     * @return 统一响应；{@code data} 为 {@code {token}}
     */
    @GetMapping(PASSCODE_PATH)
    public ApiResponse<PasscodeData> getPasscode(
            @RequestParam(name = "intention_id") @NotBlank String intentionId) {
        return ApiResponse.ok(new PasscodeData(sellerIntentionService.getPasscode(intentionId)));
    }
}
