package com.simpleshop.aftersale.controller.buyer;

import jakarta.servlet.http.HttpServletRequest;
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

import com.simpleshop.aftersale.dto.AftersaleAppliedData;
import com.simpleshop.aftersale.dto.AftersaleApplyRequest;
import com.simpleshop.aftersale.dto.AftersaleData;
import com.simpleshop.aftersale.dto.AftersaleDetailData;
import com.simpleshop.aftersale.service.BuyerAftersaleService;
import com.simpleshop.common.api.ApiResponse;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.common.auth.CurrentUser;

/**
 * 买家售后：提交申请、我的售后、详情。归属由令牌主体决定。
 */
@RestController
@RequestMapping("/api/buyer/aftersales")
@Tag(name = "买家售后")
@SecurityRequirement(name = "bearerAuth")
public class BuyerAftersaleController {

    private final BuyerAftersaleService aftersales;

    public BuyerAftersaleController(BuyerAftersaleService aftersales) {
        this.aftersales = aftersales;
    }

    @PostMapping
    @Operation(summary = "申请售后：按订单项、仅交易完成订单可申请；图片先走 /api/buyer/media 上传")
    public ApiResponse<AftersaleAppliedData> apply(
            @Valid @RequestBody AftersaleApplyRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(aftersales.apply(buyerId(httpRequest), request));
    }

    @GetMapping
    @Operation(summary = "我的售后；可按 status、order_id 筛选，按申请时间倒序分页")
    public ApiResponse<PageResponse<AftersaleData>> list(
            @RequestParam(required = false) String status,
            @RequestParam(name = "order_id", required = false) String orderId,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                aftersales.list(buyerId(httpRequest), status, orderId, page, pageSize));
    }

    @GetMapping("/{id}")
    @Operation(summary = "售后详情：只能看自己的，否则 81001")
    public ApiResponse<AftersaleDetailData> detail(
            @PathVariable String id, HttpServletRequest httpRequest) {
        return ApiResponse.ok(aftersales.detail(buyerId(httpRequest), id));
    }

    private static String buyerId(HttpServletRequest request) {
        return CurrentUser.require(request).subjectId();
    }
}
