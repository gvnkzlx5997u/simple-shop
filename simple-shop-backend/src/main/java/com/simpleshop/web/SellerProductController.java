package com.simpleshop.web;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.simpleshop.service.ImageStorageService;
import com.simpleshop.service.SellerGoodsService;
import com.simpleshop.service.dto.GoodsData;
import com.simpleshop.service.dto.ImageData;
import com.simpleshop.service.dto.PublishGoodsCommand;
import com.simpleshop.service.dto.PublishResultData;
import com.simpleshop.web.dto.PublishGoodsRequest;

/**
 * 商品接口（{@code I11-04} ~ {@code I11-09}）。方案 §4.4。
 *
 * <table border="1">
 *   <caption>接口一览</caption>
 *   <tr><th>编号</th><th>方法 路径</th><th>出参 {@code data}</th></tr>
 *   <tr><td>{@code I11-04}</td><td>{@code GET /api/seller/product}</td>
 *       <td>商品对象<b>或 {@code null}</b>（空态）</td></tr>
 *   <tr><td>{@code I11-05}</td><td>{@code POST /api/seller/product/image}</td>
 *       <td>{@code {pic_url}}（{@code multipart/form-data}，字段名 <b>{@code file}</b>）</td></tr>
 *   <tr><td>{@code I11-06}</td><td>{@code POST /api/seller/product}</td><td>{@code {id}}</td></tr>
 *   <tr><td>{@code I11-07}</td><td>{@code POST /api/seller/product/freeze}</td><td>更新后商品对象</td></tr>
 *   <tr><td>{@code I11-08}</td><td>{@code POST /api/seller/product/unfreeze}</td><td>更新后商品对象</td></tr>
 *   <tr><td>{@code I11-09}</td><td>{@code POST /api/seller/product/offline}</td><td>{@code null}</td></tr>
 * </table>
 *
 * <h2>⚠️ 三处易错路径／方法（方案 §4.4 的「易错点」表）</h2>
 * <ol>
 *   <li>{@code I11-05} 的 multipart 字段名是 <b>{@code file}</b>（不是 {@code image}/{@code upload}）；
 *       字段名不对会得到 {@code 50002}（由 {@code GlobalExceptionHandler} 处理
 *       {@code MissingServletRequestPartException}），<b>不是</b> {@code 20009}——
 *       因为「报文里没有这个部件」属于报文不合法，而不是「图片不合规」。</li>
 *   <li>{@code I11-07}/{@code I11-08}/{@code I11-09} 是 <b>{@code POST} 且<b>无请求体</b></b>
 *       （不是 {@code PUT}、不是 {@code PATCH}），靠「当前商品」定位——
 *       它们没有商品 ID 参数，因为商品表只有一行。</li>
 *   <li>{@code I11-04} 在无商品时返回 {@code data: null} + {@code code: 0}，
 *       而<b>不是</b> {@code 20002}（见 {@code SellerGoodsService#getCurrentGoods} 的说明）。</li>
 * </ol>
 *
 * <h2>本类不含任何业务判断</h2>
 * <p>Controller 只做三件事：绑定请求 → 调用 Service → 包 {@link ApiResponse}。
 * 校验、状态机、加锁全在 Service；本类出现 {@code if} 就说明业务规则漏到了接口层。
 * 唯一看起来像判断的是把 {@code PublishGoodsRequest} 转成 {@code PublishGoodsCommand}——
 * 那是<b>类型转换</b>，不是规则。
 */
@RestController
@RequestMapping(SellerProductController.BASE_PATH)
public class SellerProductController {

    /** 商品接口的公共前缀。方案 §4.4 的固定契约值，变更须同步接口交接说明与前端。 */
    public static final String BASE_PATH = "/api/seller/product";

    private final SellerGoodsService sellerGoodsService;
    private final ImageStorageService imageStorageService;

    public SellerProductController(SellerGoodsService sellerGoodsService,
                                   ImageStorageService imageStorageService) {
        this.sellerGoodsService = sellerGoodsService;
        this.imageStorageService = imageStorageService;
    }

    /**
     * {@code I11-04} 查询当前商品。
     *
     * <p>无商品时 {@code data} 为 {@code null}、{@code code} 为 {@code 0}
     * ——前端走 {@code P10-07} 空态（显示发布表单）。
     *
     * @return 统一响应；{@code data} 为商品对象或 {@code null}（<b>键始终存在</b>）
     */
    @GetMapping
    public ApiResponse<GoodsData> getCurrentGoods() {
        return ApiResponse.ok(sellerGoodsService.getCurrentGoods());
    }

    /**
     * {@code I11-05} 上传商品图片。
     *
     * <p>{@code multipart/form-data}，字段名必须是 <b>{@code file}</b>。
     * 成功返回 {@code {pic_url}}，前端把它原样回填到 {@code I11-06} 的 {@code pic_url}。
     *
     * <p>校验在 {@link ImageStorageService#store}：空文件、超过 5MB、内容不是 JPG/PNG
     * （读魔数判定，<b>不</b>看扩展名与 {@code Content-Type}）→ {@code 20009}。
     *
     * @param file multipart 中名为 {@code file} 的部分
     * @return 统一响应；{@code data} 为 {@code {pic_url}}
     */
    @PostMapping("/image")
    public ApiResponse<ImageData> uploadImage(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(imageStorageService.store(file));
    }

    /**
     * {@code I11-06} 发布商品。
     *
     * <p>无「修改」接口（{@code BR-15}：发布后名称／描述／图片／价格都不可改）。
     * 已有在售或冻结商品时返回 {@code 20001}（{@code M10-24}）。
     *
     * @param request 发布请求体
     * @return 统一响应；{@code data} 为 {@code {id}}
     */
    @PostMapping
    public ApiResponse<PublishResultData> publishGoods(@Valid @RequestBody PublishGoodsRequest request) {
        return ApiResponse.ok(sellerGoodsService.publishGoods(new PublishGoodsCommand(
                request.getName(), request.getDescription(), request.getPicUrl(), request.getPrice())));
    }

    /**
     * {@code I11-07} 手动冻结当前商品。
     *
     * <p><b>不校验队列是否为空</b>（澄清 Q18、{@code PS-02}：在售即可冻结）。
     *
     * @return 统一响应；{@code data} 为更新后的商品对象
     */
    @PostMapping("/freeze")
    public ApiResponse<GoodsData> freezeGoods() {
        return ApiResponse.ok(sellerGoodsService.freezeGoods());
    }

    /**
     * {@code I11-08} 手动解冻当前商品。
     *
     * <p>交易冻结（{@code freeze_by = trade}）时返回 {@code 20005}（{@code M10-15}）——
     * <b>服务端必须自己拒绝，不得依赖前端禁用按钮</b>。
     *
     * @return 统一响应；{@code data} 为更新后的商品对象
     */
    @PostMapping("/unfreeze")
    public ApiResponse<GoodsData> unfreezeGoods() {
        return ApiResponse.ok(sellerGoodsService.unfreezeGoods());
    }

    /**
     * {@code I11-09} 手动下架当前商品（同事务内归档）。
     *
     * <p>队列非空时返回 {@code 20006}（{@code M10-16}）；成功时 {@code data} 为 {@code null}。
     *
     * @return 统一响应；{@code data} 为 {@code null}（<b>键始终存在</b>）
     */
    @PostMapping("/offline")
    public ApiResponse<Void> takeGoodsOffline() {
        sellerGoodsService.takeGoodsOffline();
        return ApiResponse.ok();
    }
}
