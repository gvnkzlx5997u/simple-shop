package com.simpleshop.category.controller.seller;

import java.util.List;

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

import com.simpleshop.category.dto.CategoryData;
import com.simpleshop.category.dto.CategoryNodeData;
import com.simpleshop.category.dto.CreateCategoryL1Request;
import com.simpleshop.category.dto.CreateCategoryL2Request;
import com.simpleshop.category.dto.RenameCategoryRequest;
import com.simpleshop.category.service.CategoryService;
import com.simpleshop.common.api.ApiResponse;

/**
 * 卖家类别管理：查看类别树、新增/重命名/删除两级类别。
 */
@RestController
@RequestMapping("/api/seller/categories")
@Tag(name = "卖家类别")
@SecurityRequirement(name = "bearerAuth")
public class SellerCategoryController {

    private final CategoryService categoryService;

    public SellerCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "类别树")
    public ApiResponse<List<CategoryNodeData>> tree() {
        return ApiResponse.ok(categoryService.tree());
    }

    @PostMapping("/l1")
    @Operation(summary = "新增一级类别")
    public ApiResponse<CategoryData> createL1(@RequestBody CreateCategoryL1Request request) {
        return ApiResponse.ok(categoryService.createL1(request.name()));
    }

    @PutMapping("/l1/{id}")
    @Operation(summary = "重命名一级类别")
    public ApiResponse<CategoryData> renameL1(
            @PathVariable String id, @RequestBody RenameCategoryRequest request) {
        return ApiResponse.ok(categoryService.renameL1(id, request.name()));
    }

    @DeleteMapping("/l1/{id}")
    @Operation(summary = "删除一级类别；要求没有二级子类")
    public ApiResponse<Void> deleteL1(@PathVariable String id) {
        categoryService.deleteL1(id);
        return ApiResponse.ok();
    }

    @PostMapping("/l2")
    @Operation(summary = "新增二级类别")
    public ApiResponse<CategoryData> createL2(@RequestBody CreateCategoryL2Request request) {
        return ApiResponse.ok(categoryService.createL2(request.l1Id(), request.name()));
    }

    @PutMapping("/l2/{id}")
    @Operation(summary = "重命名二级类别")
    public ApiResponse<CategoryData> renameL2(
            @PathVariable String id, @RequestBody RenameCategoryRequest request) {
        return ApiResponse.ok(categoryService.renameL2(id, request.name()));
    }

    @DeleteMapping("/l2/{id}")
    @Operation(summary = "删除二级类别；要求没有当前商品引用")
    public ApiResponse<Void> deleteL2(@PathVariable String id) {
        categoryService.deleteL2(id);
        return ApiResponse.ok();
    }
}
