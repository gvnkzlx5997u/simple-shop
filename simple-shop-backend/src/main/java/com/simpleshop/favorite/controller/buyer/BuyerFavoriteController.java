package com.simpleshop.favorite.controller.buyer;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.simpleshop.common.api.ApiResponse;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.common.auth.CurrentUser;
import com.simpleshop.favorite.dto.AddFavoriteRequest;
import com.simpleshop.favorite.dto.FavoriteItemData;
import com.simpleshop.favorite.dto.FavoriteStatusData;
import com.simpleshop.favorite.service.FavoriteService;

/**
 * 买家收藏：收藏、取消收藏、我的收藏。卖家令牌由拦截器按角色拒绝。
 */
@RestController
@RequestMapping("/api/buyer/favorites")
@Tag(name = "买家收藏")
public class BuyerFavoriteController {

    private final FavoriteService favoriteService;

    public BuyerFavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping
    @Operation(summary = "收藏；重复收藏只有一行，商品不在售返回 70002")
    public ApiResponse<Void> add(
            @Valid @RequestBody AddFavoriteRequest request, HttpServletRequest httpRequest) {
        favoriteService.add(buyerId(httpRequest), request.goodsId());
        return ApiResponse.ok();
    }

    @GetMapping
    @Operation(summary = "我的收藏；按收藏时间倒序，已下架行保留且 detail_available=false")
    public ApiResponse<PageResponse<FavoriteItemData>> list(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(favoriteService.list(buyerId(httpRequest), page, pageSize));
    }

    @GetMapping("/{goodsId}")
    @Operation(summary = "当前买家是否收藏了这件商品；详情页据此显示「收藏 / 已收藏」")
    public ApiResponse<FavoriteStatusData> favorited(
            @PathVariable String goodsId, HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                new FavoriteStatusData(favoriteService.favorited(buyerId(httpRequest), goodsId)));
    }

    @DeleteMapping("/{goodsId}")
    @Operation(summary = "取消收藏；不要求商品在售，行不存在也返回成功")
    public ApiResponse<Void> remove(@PathVariable String goodsId, HttpServletRequest httpRequest) {
        favoriteService.remove(buyerId(httpRequest), goodsId);
        return ApiResponse.ok();
    }

    private static String buyerId(HttpServletRequest request) {
        return CurrentUser.require(request).subjectId();
    }
}
