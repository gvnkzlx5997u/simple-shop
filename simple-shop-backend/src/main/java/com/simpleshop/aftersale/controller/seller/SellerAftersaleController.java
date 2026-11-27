package com.simpleshop.aftersale.controller.seller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.simpleshop.aftersale.dto.AftersaleCompleteRequest;
import com.simpleshop.aftersale.dto.AftersaleData;
import com.simpleshop.aftersale.dto.AftersaleDetailData;
import com.simpleshop.aftersale.service.SellerAftersaleService;
import com.simpleshop.common.api.ApiResponse;
import com.simpleshop.common.api.PageResponse;

/**
 * 卖家售后：查看全部、登记结果、取消。卖家可以看到所有买家的售后单。
 */
@RestController
@RequestMapping("/api/seller/aftersales")
@Tag(name = "卖家售后")
@SecurityRequirement(name = "bearerAuth")
public class SellerAftersaleController {

    private final SellerAftersaleService aftersales;

    public SellerAftersaleController(SellerAftersaleService aftersales) {
        this.aftersales = aftersales;
    }

    @GetMapping
    @Operation(summary = "售后列表；可按 status、order_no 筛选，按申请时间倒序分页")
    public ApiResponse<PageResponse<AftersaleData>> list(
            @RequestParam(required = false) String status,
            @RequestParam(name = "order_no", required = false) String orderNo,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ApiResponse.ok(aftersales.list(status, orderNo, page, pageSize));
    }

    @GetMapping("/{id}")
    @Operation(summary = "售后详情：含订单项快照与图片")
    public ApiResponse<AftersaleDetailData> detail(@PathVariable String id) {
        return ApiResponse.ok(aftersales.detail(id));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "登记结果并完成；EXCHANGE 不退款，退款类结果占用该订单项的退款额度（重复 81004）")
    public ApiResponse<AftersaleData> complete(
            @PathVariable String id, @Valid @RequestBody AftersaleCompleteRequest request) {
        return ApiResponse.ok(aftersales.complete(id, request));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消/拒绝：已取消幂等返回成功，已完成 81002")
    public ApiResponse<AftersaleData> cancel(@PathVariable String id) {
        return ApiResponse.ok(aftersales.cancel(id));
    }
}
