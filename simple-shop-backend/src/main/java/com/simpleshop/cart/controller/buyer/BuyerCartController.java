package com.simpleshop.cart.controller.buyer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.simpleshop.cart.dto.AddCartItemRequest;
import com.simpleshop.cart.dto.CartListData;
import com.simpleshop.cart.dto.MoveToFavoritesData;
import com.simpleshop.cart.dto.MoveToFavoritesRequest;
import com.simpleshop.cart.service.CartService;
import com.simpleshop.cart.service.CartToFavoriteService;
import com.simpleshop.common.api.ApiResponse;
import com.simpleshop.common.auth.CurrentUser;

/**
 * 买家购物车：加购、列表、移除、批量转收藏。卖家令牌由拦截器按角色拒绝。
 */
@RestController
@RequestMapping("/api/buyer/cart")
@Tag(name = "买家购物车")
public class BuyerCartController {

    private final CartService cartService;
    private final CartToFavoriteService cartToFavoriteService;

    public BuyerCartController(
            CartService cartService,
            CartToFavoriteService cartToFavoriteService) {
        this.cartService = cartService;
        this.cartToFavoriteService = cartToFavoriteService;
    }

    @PostMapping
    @Operation(summary = "加入购物车；重复加购只有一行，商品不在售返回 70001")
    public ApiResponse<Void> add(
            @Valid @RequestBody AddCartItemRequest request, HttpServletRequest httpRequest) {
        cartService.add(buyerId(httpRequest), request.goodsId());
        return ApiResponse.ok();
    }

    @GetMapping
    @Operation(summary = "购物车列表；不分页，已下架行保留且 selectable=false")
    public ApiResponse<CartListData> list(HttpServletRequest httpRequest) {
        return ApiResponse.ok(cartService.list(buyerId(httpRequest)));
    }

    @DeleteMapping("/{goodsId}")
    @Operation(summary = "移除一件；行不存在也返回成功")
    public ApiResponse<Void> remove(@PathVariable String goodsId, HttpServletRequest httpRequest) {
        cartService.remove(buyerId(httpRequest), goodsId);
        return ApiResponse.ok();
    }

    @PostMapping("/move-to-favorites")
    @Operation(summary = "批量转收藏；含已下架商品时整批拒绝并返回 70003")
    public ApiResponse<MoveToFavoritesData> moveToFavorites(
            @Valid @RequestBody MoveToFavoritesRequest request, HttpServletRequest httpRequest) {
        int moved = cartToFavoriteService.moveToFavorites(buyerId(httpRequest), request.goodsIds());
        return ApiResponse.ok(new MoveToFavoritesData(moved));
    }

    private static String buyerId(HttpServletRequest request) {
        return CurrentUser.require(request).subjectId();
    }
}
