package com.simpleshop.order.controller.seller;

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
import com.simpleshop.order.dto.OrderData;
import com.simpleshop.order.dto.OrderItemsData;
import com.simpleshop.order.dto.OrderStatusData;
import com.simpleshop.order.dto.ShipLogisticsRequest;
import com.simpleshop.order.service.SellerOrderQueryService;
import com.simpleshop.order.service.SellerOrderService;

/**
 * 卖家订单：列表筛选、详情、明细与状态推进。
 *
 * <p>订单管理、按客户、按商品三个入口在后端只是筛选条件，进入详情后一律按订单主键查。
 * 卖家最多把订单推到已发货：交易完成由买家确认收货推进，这里没有完成接口。
 */
@RestController
@RequestMapping("/api/seller/orders")
@Tag(name = "卖家订单")
@SecurityRequirement(name = "bearerAuth")
public class SellerOrderController {

    private final SellerOrderService orderService;
    private final SellerOrderQueryService queryService;

    public SellerOrderController(
            SellerOrderService orderService, SellerOrderQueryService queryService) {
        this.orderService = orderService;
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "订单列表；可按状态、客户、商品组合筛选，按下单时间倒序分页")
    public ApiResponse<PageResponse<OrderData>> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "buyer_id", required = false) String buyerId,
            @RequestParam(name = "goods_id", required = false) String goodsId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ApiResponse.ok(queryService.list(status, buyerId, goodsId, page, pageSize));
    }

    @GetMapping("/{id}")
    @Operation(summary = "订单详情；不返回订单项，明细按订单 ID 另行查询")
    public ApiResponse<OrderData> detail(@PathVariable String id) {
        return ApiResponse.ok(queryService.detail(id));
    }

    @GetMapping("/{id}/items")
    @Operation(summary = "订单项；按订单 ID 单独查询，不分页")
    public ApiResponse<OrderItemsData> items(@PathVariable String id) {
        return ApiResponse.ok(queryService.items(id));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "确认订单：待确认 → 已确认")
    public ApiResponse<OrderStatusData> confirm(@PathVariable String id) {
        return ApiResponse.ok(orderService.confirm(id));
    }

    @PostMapping("/{id}/prepare")
    @Operation(summary = "标记备货完成：已确认 → 备货完成，不校验库存")
    public ApiResponse<OrderStatusData> prepare(@PathVariable String id) {
        return ApiResponse.ok(orderService.prepare(id));
    }

    @PostMapping("/{id}/ship")
    @Operation(summary = "开始发货：备货完成 → 已发货，必须填承运商与单号；此后买家不可取消")
    public ApiResponse<OrderStatusData> ship(
            @PathVariable String id, @Valid @RequestBody ShipLogisticsRequest request) {
        return ApiResponse.ok(orderService.ship(id, request));
    }

    @PutMapping("/{id}/logistics")
    @Operation(summary = "修改物流：发货后修正承运商、单号与备注，订单状态不变；已取消订单 82002")
    public ApiResponse<OrderStatusData> updateLogistics(
            @PathVariable String id, @Valid @RequestBody ShipLogisticsRequest request) {
        return ApiResponse.ok(orderService.updateLogistics(id, request));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消订单：交易完成前可取消，已取消时重复调用返回成功")
    public ApiResponse<OrderStatusData> cancel(@PathVariable String id) {
        return ApiResponse.ok(orderService.cancel(id));
    }
}
