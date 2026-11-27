package com.simpleshop.address.controller.buyer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.simpleshop.address.dto.AddressData;
import com.simpleshop.address.dto.AddressListData;
import com.simpleshop.address.dto.AddressRequest;
import com.simpleshop.address.service.AddressService;
import com.simpleshop.common.api.ApiResponse;
import com.simpleshop.common.auth.CurrentUser;

/**
 * 买家地址簿：列表、新建、编辑、删除、设默认。
 *
 * <p>归属一律由令牌主体决定，路径只带地址 ID；他人地址返回 80001（按不存在处理）。
 */
@RestController
@RequestMapping("/api/buyer/addresses")
@Tag(name = "买家地址")
@SecurityRequirement(name = "bearerAuth")
public class BuyerAddressController {

    private final AddressService addressService;

    public BuyerAddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    @Operation(summary = "地址列表；默认地址排最前，不分页（上限 10 条）")
    public ApiResponse<AddressListData> list(HttpServletRequest httpRequest) {
        return ApiResponse.ok(addressService.list(buyerId(httpRequest)));
    }

    @PostMapping
    @Operation(summary = "新建地址；第一条自动成为默认，超过 10 条返回 80002")
    public ApiResponse<AddressData> create(
            @Valid @RequestBody AddressRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(addressService.create(buyerId(httpRequest), request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑地址；不改默认标记")
    public ApiResponse<AddressData> update(
            @PathVariable String id,
            @Valid @RequestBody AddressRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(addressService.update(buyerId(httpRequest), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除地址；删的是默认地址时由最新一条补位")
    public ApiResponse<Void> delete(@PathVariable String id, HttpServletRequest httpRequest) {
        addressService.delete(buyerId(httpRequest), id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/default")
    @Operation(summary = "设为默认；原默认自动取消")
    public ApiResponse<AddressData> setDefault(
            @PathVariable String id, HttpServletRequest httpRequest) {
        return ApiResponse.ok(addressService.setDefault(buyerId(httpRequest), id));
    }

    private static String buyerId(HttpServletRequest request) {
        return CurrentUser.require(request).subjectId();
    }
}
