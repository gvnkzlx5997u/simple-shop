package com.simpleshop.account.controller.seller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.simpleshop.account.dto.CustomerDetailData;
import com.simpleshop.account.dto.CustomerSummaryData;
import com.simpleshop.account.service.SellerCustomerService;
import com.simpleshop.common.api.ApiResponse;
import com.simpleshop.common.api.PageResponse;

/**
 * 卖家客户管理：只读的注册客户列表与详情；不提供搜索、统计或导出。
 */
@RestController
@Tag(name = "卖家客户")
@SecurityRequirement(name = "bearerAuth")
public class SellerCustomerController {

    private final SellerCustomerService customerService;

    public SellerCustomerController(SellerCustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/api/seller/customers")
    @Operation(summary = "注册客户列表；按注册时间倒序分页")
    public ApiResponse<PageResponse<CustomerSummaryData>> list(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ApiResponse.ok(customerService.list(page, pageSize));
    }

    @GetMapping("/api/seller/customers/{id}")
    @Operation(summary = "客户详情与购买历史；购买历史分页参数作用于历史并集")
    public ApiResponse<CustomerDetailData> detail(
            @PathVariable String id,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ApiResponse.ok(customerService.detail(id, page, pageSize));
    }
}
