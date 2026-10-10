package com.simpleshop.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.simpleshop.service.SellerHistoryService;
import com.simpleshop.service.dto.HistoryGoodsDetailData;
import com.simpleshop.service.dto.HistoryPageData;

/**
 * 历史商品接口（{@code I11-15}、{@code I11-16}）。方案 §4.4。
 *
 * <table border="1">
 *   <caption>接口一览</caption>
 *   <tr><th>编号</th><th>方法 路径</th><th>出参 {@code data}</th></tr>
 *   <tr><td>{@code I11-15}</td><td>{@code GET /api/seller/history/products}</td>
 *       <td>分页 + {@code items[]}（按 {@code trade_end} 倒序）</td></tr>
 *   <tr><td>{@code I11-16}</td><td>{@code GET /api/seller/history/products/{id}}</td>
 *       <td>详情 + {@code intentions[]}（含 {@code trades[]}）</td></tr>
 * </table>
 *
 * <h2>⚠️ {@code I11-16} 是 16 个接口里唯一带路径参数的（{@code {id}}）</h2>
 * <p>其余接口要么靠「当前商品」定位（单一商品槽位，{@code BR-11}），要么把 ID 放在请求体／
 * query 里。这里必须用路径参数，因为「看哪一个历史商品」正是这个端点的资源语义。
 *
 * <h2>⚠️ 路径顺序陷阱：{@code /products/{id}} 不能吃掉 {@code /products}</h2>
 * <p>两个映射的路径不同（一个有 {@code /{id}}、一个没有），Spring 会选最具体的那条，
 * 不存在 {@code /products} 被 {@code {id}} 匹配走的情形。这是**有意**保持两条独立映射的原因——
 * 若把列表也写成 {@code /products/{id}} 并把 {@code id} 设为可空，那就得在方法里判空，
 * 且「不带 id」的 404 语义会变得含糊。
 *
 * <h2>本类不含任何业务判断</h2>
 * <p>与其它 Controller 一致：绑定请求 → 调 Service → 包 {@link ApiResponse}。
 * 唯一的分页注解（{@code @Min}／{@code @Max}）与 {@code SellerIntentionController} 同口径：
 * {@code page}／{@code page_size} 在 §4.3 里<b>没有</b>专属业务码，故注解可以留；
 * Service 仍<b>独立</b>校验一次，两处返回同一个 {@code 50002}。
 */
@RestController
public class SellerHistoryController {

    /** {@code I11-15} 历史商品列表；{@code I11-16} 在本路径后追加 {@code /{id}}。 */
    public static final String PRODUCTS_PATH = "/api/seller/history/products";

    private final SellerHistoryService sellerHistoryService;

    public SellerHistoryController(SellerHistoryService sellerHistoryService) {
        this.sellerHistoryService = sellerHistoryService;
    }

    /**
     * {@code I11-15} 历史商品列表（分页，按交易结束时间倒序）。
     *
     * <p>契约只给 {@code page}／{@code page_size} 两个入参，<b>不支持任何筛选</b>（{@code FR-11}）。
     * 历史为空是正常状态：{@code total = 0、items = []}，不是 {@code data: null}。
     *
     * @param page     页码（<b>从 1 起</b>，默认 1）
     * @param pageSize 每页条数（默认 10，即 {@code 10-E} 的口径；上限 100）
     * @return 统一响应；{@code data} 恒为一个对象（<b>键始终存在</b>）
     */
    @GetMapping(PRODUCTS_PATH)
    public ApiResponse<HistoryPageData> listHistory(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "page_size", defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.ok(sellerHistoryService.listHistory(page, pageSize));
    }

    /**
     * {@code I11-16} 历史商品详情。
     *
     * <p>该历史商品不存在时返回 {@code 20011} + <b>HTTP 404</b>（归商品域，不得越域占用
     * {@code 4xxxx}）——由 Service 抛，{@code GlobalExceptionHandler} 按
     * {@code ErrorCode.httpStatus()} 输出。
     *
     * @param id 历史商品 ID（= 原商品 ID，前缀 {@code G}）
     * @return 统一响应；{@code data} 为详情（含 {@code intentions[]}，每条含 {@code trades[]}）
     */
    @GetMapping(PRODUCTS_PATH + "/{id}")
    public ApiResponse<HistoryGoodsDetailData> getHistoryDetail(@PathVariable("id") String id) {
        return ApiResponse.ok(sellerHistoryService.getHistoryDetail(id));
    }
}
