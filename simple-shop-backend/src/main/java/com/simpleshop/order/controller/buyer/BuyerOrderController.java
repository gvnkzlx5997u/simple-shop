package com.simpleshop.order.controller.buyer;

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

import com.simpleshop.common.api.ApiResponse;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.common.auth.AuthPrincipal;
import com.simpleshop.common.auth.CurrentUser;
import com.simpleshop.order.dto.OrderData;
import com.simpleshop.order.dto.OrderItemsData;
import com.simpleshop.order.dto.OrderPlacedData;
import com.simpleshop.order.dto.OrderStatusData;
import com.simpleshop.order.dto.PlaceOrderRequest;
import com.simpleshop.order.service.BuyerOrderQueryService;
import com.simpleshop.order.service.BuyerOrderService;

/**
 * 买家下单、取消与订单查询。订单归属由令牌主体决定，请求体与路径都不接受买家 ID。
 */
@RestController
@RequestMapping("/api/buyer/orders")
@Tag(name = "买家订单")
@SecurityRequirement(name = "bearerAuth")
public class BuyerOrderController {

    private final BuyerOrderService orderService;
    private final BuyerOrderQueryService queryService;

    public BuyerOrderController(
            BuyerOrderService orderService, BuyerOrderQueryService queryService) {
        this.orderService = orderService;
        this.queryService = queryService;
    }

    @PostMapping
    @Operation(summary = "提交订单；整单受理或整单拒绝，不预占库存")
    public ApiResponse<OrderPlacedData> place(
            @Valid @RequestBody PlaceOrderRequest request, HttpServletRequest httpRequest) {
        AuthPrincipal buyer = CurrentUser.require(httpRequest);
        return ApiResponse.ok(orderService.place(buyer.subjectId(), request));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消自己的订单；已发货后不可取消，已取消时重复调用返回成功")
    public ApiResponse<OrderStatusData> cancel(
            @PathVariable String id, HttpServletRequest httpRequest) {
        AuthPrincipal buyer = CurrentUser.require(httpRequest);
        return ApiResponse.ok(orderService.cancel(buyer.subjectId(), id));
    }

    @GetMapping
    @Operation(summary = "我的订单；全部状态，可按状态筛选，按下单时间倒序分页")
    public ApiResponse<PageResponse<OrderData>> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            HttpServletRequest httpRequest) {
        AuthPrincipal buyer = CurrentUser.require(httpRequest);
        return ApiResponse.ok(queryService.list(buyer.subjectId(), status, page, pageSize));
    }

    @GetMapping("/{id}")
    @Operation(summary = "订单详情；不返回订单项，明细按订单 ID 另行查询")
    public ApiResponse<OrderData> detail(
            @PathVariable String id, HttpServletRequest httpRequest) {
        AuthPrincipal buyer = CurrentUser.require(httpRequest);
        return ApiResponse.ok(queryService.detail(buyer.subjectId(), id));
    }

    @GetMapping("/{id}/items")
    @Operation(summary = "订单项；按订单 ID 单独查询，不分页")
    public ApiResponse<OrderItemsData> items(
            @PathVariable String id, HttpServletRequest httpRequest) {
        AuthPrincipal buyer = CurrentUser.require(httpRequest);
        return ApiResponse.ok(queryService.items(buyer.subjectId(), id));
    }
}
