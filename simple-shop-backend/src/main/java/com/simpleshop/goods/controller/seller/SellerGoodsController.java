package com.simpleshop.goods.controller.seller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.simpleshop.common.api.ApiResponse;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.goods.dto.GoodsCategoryRequest;
import com.simpleshop.goods.dto.GoodsDetailData;
import com.simpleshop.goods.dto.GoodsPublishRequest;
import com.simpleshop.goods.dto.GoodsStockRequest;
import com.simpleshop.goods.dto.GoodsSummaryData;
import com.simpleshop.goods.service.SellerGoodsQueryService;
import com.simpleshop.goods.service.SellerGoodsService;

/**
 * 卖家商品管理：发布、列表、详情、库存、上下架与改挂类别。
 */
@RestController
@RequestMapping("/api/seller/goods")
@Tag(name = "卖家商品")
@SecurityRequirement(name = "bearerAuth")
public class SellerGoodsController {

    private final SellerGoodsService goodsService;
    private final SellerGoodsQueryService queryService;

    public SellerGoodsController(
            SellerGoodsService goodsService, SellerGoodsQueryService queryService) {
        this.goodsService = goodsService;
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "商品列表；可按状态、下架原因、类别或名称筛选")
    public ApiResponse<PageResponse<GoodsSummaryData>> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "off_shelf_reason", required = false) String offShelfReason,
            @RequestParam(name = "category_id", required = false) String categoryId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ApiResponse.ok(queryService.list(
                status, offShelfReason, categoryId, keyword, page, pageSize));
    }

    @PostMapping
    @Operation(summary = "发布商品；图片 URL 先通过 /api/seller/media 上传")
    public ApiResponse<GoodsDetailData> publish(@Valid @RequestBody GoodsPublishRequest request) {
        return ApiResponse.ok(goodsService.publish(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "商品详情，包含全部状态")
    public ApiResponse<GoodsDetailData> detail(@PathVariable String id) {
        return ApiResponse.ok(queryService.detail(id));
    }

    @PutMapping("/{id}/stock")
    @Operation(summary = "修改库存；在售改 0 触发库存耗尽下架，库存耗尽下架改大于 0 自动上架；手动下架后库存只读")
    public ApiResponse<GoodsDetailData> updateStock(
            @PathVariable String id, @Valid @RequestBody GoodsStockRequest request) {
        return ApiResponse.ok(goodsService.updateStock(id, request.stock()));
    }

    @PostMapping("/{id}/off-shelf")
    @Operation(summary = "手动下架；手动下架是终态，没有重新上架接口")
    public ApiResponse<GoodsDetailData> offShelf(@PathVariable String id) {
        return ApiResponse.ok(goodsService.offShelf(id));
    }

    @PutMapping("/{id}/category")
    @Operation(summary = "商品改挂二级类别；所有状态均可改挂")
    public ApiResponse<GoodsDetailData> changeCategory(
            @PathVariable String id, @Valid @RequestBody GoodsCategoryRequest request) {
        return ApiResponse.ok(goodsService.changeCategory(id, request.categoryId()));
    }
}
