package com.simpleshop.category.controller.publicapi;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.simpleshop.category.dto.CategoryNodeData;
import com.simpleshop.category.service.CategoryService;
import com.simpleshop.common.api.ApiResponse;

/**
 * 公共类别树，不需要登录。
 */
@RestController
@RequestMapping("/api/public/categories")
@Tag(name = "公共类别")
public class PublicCategoryController {

    private final CategoryService categoryService;

    public PublicCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "类别树")
    public ApiResponse<List<CategoryNodeData>> tree() {
        return ApiResponse.ok(categoryService.tree());
    }
}
