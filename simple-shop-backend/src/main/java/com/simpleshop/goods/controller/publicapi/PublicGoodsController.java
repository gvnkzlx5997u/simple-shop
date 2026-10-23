package com.simpleshop.goods.controller.publicapi;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.simpleshop.common.api.ApiResponse;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.goods.dto.GoodsDetailData;
import com.simpleshop.goods.dto.GoodsSummaryData;
import com.simpleshop.goods.service.BuyerGoodsQueryService;

/**
 * 公共商品浏览与搜索，不需要登录。
 */
@RestController
@RequestMapping("/api/public/goods")
@Tag(name = "公共商品")
public class PublicGoodsController {

    private final BuyerGoodsQueryService goodsService;

    public PublicGoodsController(BuyerGoodsQueryService goodsService) {
        this.goodsService = goodsService;
    }

    @GetMapping
    @Operation(summary = "在售商品列表/搜索；category_id 可传一级或二级")
    public ApiResponse<PageResponse<GoodsSummaryData>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "category_id", required = false) String categoryId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ApiResponse.ok(goodsService.list(keyword, categoryId, page, pageSize));
    }

    @GetMapping("/{id}")
    @Operation(summary = "商品详情；在售和交易中可见，下架商品返回 404")
    public ApiResponse<GoodsDetailData> detail(@PathVariable String id) {
        return ApiResponse.ok(goodsService.detail(id));
    }
}
