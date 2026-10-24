package com.simpleshop.history.controller.seller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.simpleshop.common.api.ApiResponse;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.history.dto.GoodsHistoryDetailData;
import com.simpleshop.history.dto.GoodsHistorySummaryData;
import com.simpleshop.history.service.SellerHistoryService;

/**
 * 卖家历史记录：成交售罄归档的商品，只读。
 */
@RestController
@Tag(name = "卖家历史")
@SecurityRequirement(name = "bearerAuth")
public class SellerHistoryController {

    private final SellerHistoryService historyService;

    public SellerHistoryController(SellerHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/api/seller/history/goods")
    @Operation(summary = "历史商品列表；按归档时间倒序分页")
    public ApiResponse<PageResponse<GoodsHistorySummaryData>> list(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ApiResponse.ok(historyService.list(page, pageSize));
    }

    @GetMapping("/api/seller/history/goods/{id}")
    @Operation(summary = "历史商品详情；含描述与图片快照及全部历史意向")
    public ApiResponse<GoodsHistoryDetailData> detail(@PathVariable String id) {
        return ApiResponse.ok(historyService.detail(id));
    }
}
