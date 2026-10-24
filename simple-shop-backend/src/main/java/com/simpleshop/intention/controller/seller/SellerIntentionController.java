package com.simpleshop.intention.controller.seller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.simpleshop.common.api.ApiResponse;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.intention.dto.IntentionEntryData;
import com.simpleshop.intention.dto.TradeFailureRequest;
import com.simpleshop.intention.dto.TradeResultData;
import com.simpleshop.intention.service.SellerIntentionQueryService;
import com.simpleshop.intention.service.SellerTradeService;

/**
 * 卖家意向：名单查询与交易裁决。名单按商品、裁决按意向，路径前缀不同，方法级写全路径。
 */
@RestController
@Tag(name = "卖家意向")
@SecurityRequirement(name = "bearerAuth")
public class SellerIntentionController {

    private final SellerIntentionQueryService queryService;
    private final SellerTradeService tradeService;

    public SellerIntentionController(
            SellerIntentionQueryService queryService, SellerTradeService tradeService) {
        this.queryService = queryService;
        this.tradeService = tradeService;
    }

    @GetMapping("/api/seller/goods/{goodsId}/intentions")
    @Operation(summary = "意向名单；按队列序号升序分页，可按状态筛选")
    public ApiResponse<PageResponse<IntentionEntryData>> list(
            @PathVariable String goodsId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ApiResponse.ok(queryService.list(goodsId, status, page, pageSize));
    }

    @PostMapping("/api/seller/intentions/{id}/trade")
    @Operation(summary = "选定队首进入交易；商品在售、无其他交易中意向、且必须是队首")
    public ApiResponse<IntentionEntryData> enterTrade(@PathVariable String id) {
        return ApiResponse.ok(tradeService.enterTrade(id));
    }

    @PostMapping("/api/seller/intentions/{id}/success")
    @Operation(summary = "标记交易成功；扣减库存，扣减后为 0 时商品与全部意向同事务归档")
    public ApiResponse<TradeResultData> markSuccess(@PathVariable String id) {
        return ApiResponse.ok(tradeService.markSuccess(id));
    }

    @PostMapping("/api/seller/intentions/{id}/failure")
    @Operation(summary = "标记交易失败并裁决：作废，或重新排队到队尾；商品回在售，库存不变")
    public ApiResponse<TradeResultData> markFailure(
            @PathVariable String id, @RequestBody TradeFailureRequest request) {
        return ApiResponse.ok(tradeService.markFailure(id, request));
    }
}
